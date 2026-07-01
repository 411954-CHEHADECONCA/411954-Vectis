package com.vectis.backend.service;

import com.vectis.backend.domain.entity.FciVcpSnapshot;
import com.vectis.backend.domain.entity.InvestmentAsset;
import com.vectis.backend.domain.entity.InvestmentAssetType;
import com.vectis.backend.domain.entity.InvestmentValuation;
import com.vectis.backend.dto.FciFundDto;
import com.vectis.backend.dto.macro.ArgentinadatosFciDto;
import com.vectis.backend.dto.macro.ArgentinadatosFciHistoricoDto;
import com.vectis.backend.dto.macro.ArgentinadatosFciHistoricoResponseDto;
import com.vectis.backend.repository.FciVcpSnapshotRepository;
import com.vectis.backend.repository.InvestmentRepository;
import com.vectis.backend.repository.InvestmentValuationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class FciValuationSyncService {

    private final RestTemplate                  macroRestTemplate;
    private final FciVcpSnapshotRepository      fciVcpSnapshotRepository;
    private final InvestmentRepository          investmentRepository;
    private final InvestmentValuationRepository valuationRepository;

    @Value("${macro.api.base-url}")
    private String macroBaseUrl;

    public FciValuationSyncService(
            @Qualifier("macroRestTemplate") RestTemplate macroRestTemplate,
            FciVcpSnapshotRepository fciVcpSnapshotRepository,
            InvestmentRepository investmentRepository,
            InvestmentValuationRepository valuationRepository) {
        this.macroRestTemplate       = macroRestTemplate;
        this.fciVcpSnapshotRepository = fciVcpSnapshotRepository;
        this.investmentRepository    = investmentRepository;
        this.valuationRepository     = valuationRepository;
    }

    private static final String[] FCI_CATEGORIAS = {"mercadoDinero", "rentaFija", "rentaVariable", "rentaMixta"};

    // ─── Startup sync ─────────────────────────────────────────────────────────

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        log.info("Iniciando sync FCI al arranque...");
        fetchFciVcp();
        syncFciValuations();
    }

    // ─── Scheduled tasks ──────────────────────────────────────────────────────

    /**
     * Fetch diario del VCP de todos los fondos FCI.
     * L-V a las 21:00 UTC (cierre del mercado).
     */
    @Scheduled(cron = "0 0 21 * * MON-FRI")
    public void fetchFciVcp() {
        for (String categoria : FCI_CATEGORIAS) {
            try {
                ArgentinadatosFciDto[] fondos = macroRestTemplate.getForObject(
                        macroBaseUrl + "/finanzas/fci/" + categoria + "/ultimo",
                        ArgentinadatosFciDto[].class);
                if (fondos == null) {
                    log.warn("Respuesta nula de argentinadatos para categoria: {}", categoria);
                    continue;
                }
                int saved = 0;
                for (ArgentinadatosFciDto dto : fondos) {
                    if (dto.fecha() == null || dto.vcp() == null) continue;
                    try {
                        boolean inserted = saveFciVcpSnapshot(dto, categoria);
                        if (inserted) saved++;
                    } catch (Exception inner) {
                        log.warn("No se pudo guardar snapshot FCI para fondo {}: {}",
                                dto.fondo(), inner.getMessage());
                    }
                }
                log.info("FCI VCP fetch completado para categoria: {} ({} snapshots nuevos)", categoria, saved);
            } catch (Exception e) {
                log.warn("No se pudo obtener VCP FCI para {}: {}", categoria, e.getMessage());
            }
        }
    }

    /**
     * Sincroniza valuaciones FCI_CUOTAPARTES desde los snapshots del día.
     * L-V a las 21:30 UTC (30 min después del fetch para asegurar disponibilidad de datos).
     */
    @Scheduled(cron = "0 30 21 * * MON-FRI")
    public void syncFciValuations() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        List<InvestmentAsset> assets = investmentRepository
                .findAllByTypeAndAutoTrackTrue(InvestmentAssetType.FCI_CUOTAPARTES);

        log.info("Sincronizando valuaciones FCI para {} activos con auto-track activo", assets.size());

        for (InvestmentAsset asset : assets) {
            try {
                String externalId = asset.getExternalId();
                if (externalId == null || externalId.isBlank()) {
                    log.debug("Activo {} no tiene externalId configurado — se omite", asset.getId());
                    continue;
                }

                fciVcpSnapshotRepository.findByFondoAndFecha(externalId, today)
                        .ifPresent(snapshot -> {
                            try {
                                saveFciValuation(asset, today, snapshot.getVcp());
                            } catch (Exception inner) {
                                log.warn("No se pudo guardar valuacion FCI para activo {}: {}",
                                        asset.getId(), inner.getMessage());
                            }
                        });
            } catch (Exception e) {
                log.warn("Error sync valuacion FCI para activo {}: {}", asset.getId(), e.getMessage());
            }
        }
    }

    // ─── Private REQUIRES_NEW helpers ────────────────────────────────────────

    /**
     * Persiste un único snapshot FCI en su propia transacción.
     * Si falla, sólo se descarta este registro; el bucle externo continúa.
     *
     * @return true si se insertó un registro nuevo, false si ya existía.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean saveFciVcpSnapshot(ArgentinadatosFciDto dto, String categoria) {
        LocalDate fecha = LocalDate.parse(dto.fecha());
        if (fciVcpSnapshotRepository.existsByFondoAndFecha(dto.fondo(), fecha)) {
            return false;
        }
        fciVcpSnapshotRepository.save(FciVcpSnapshot.builder()
                .fondo(dto.fondo())
                .categoria(categoria)
                .vcp(dto.vcp().setScale(4, RoundingMode.HALF_EVEN))
                .fecha(fecha)
                .build());
        return true;
    }

    /**
     * Persiste una única valuación FCI en su propia transacción.
     * Si falla, sólo se descarta este registro; el bucle externo continúa.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveFciValuation(InvestmentAsset asset, LocalDate date, BigDecimal vcp) {
        boolean exists = valuationRepository
                .existsByInvestmentAsset_IdAndValuationDate(asset.getId(), date);
        if (!exists) {
            valuationRepository.save(InvestmentValuation.builder()
                    .investmentAsset(asset)
                    .valuationDate(date)
                    .pricePerUnit(vcp)
                    .source("ARGENTINADATOS")
                    .build());
            log.debug("Valuacion auto-generada para activo {} en {}", asset.getId(), date);
        } else {
            log.debug("Valuacion ya existe para activo {} en {}", asset.getId(), date);
        }
    }

    // ─── Backfill histórico de valuaciones ──────────────────────────────────────

    /**
     * Rellena las valuaciones faltantes de un activo FCI_CUOTAPARTES con seguimiento automático,
     * trayendo la serie histórica de VCP del fondo desde argentinadatos
     * ({@code /finanzas/fci/fondos/{slug}/historico}) y creando una valuación por cada día disponible
     * entre la fecha de compra y hoy que aún no exista.
     *
     * <p>Muta la colección {@code asset.getValuations()} (no persiste): el llamador debe guardar el
     * activo dentro de su transacción para que el cascade persista las valuaciones nuevas.
     * Degrada con elegancia: si la API falla o no hay datos, no lanza y retorna 0.
     *
     * @return cantidad de valuaciones nuevas agregadas a la colección del activo.
     */
    public int backfillValuations(InvestmentAsset asset) {
        if (asset.getType() != InvestmentAssetType.FCI_CUOTAPARTES || !asset.isAutoTrack()) return 0;
        String fondo = asset.getExternalId();
        if (fondo == null || fondo.isBlank() || asset.getPurchaseDate() == null) return 0;

        LocalDate from = asset.getPurchaseDate();
        LocalDate to   = LocalDate.now(ZoneOffset.UTC);
        if (from.isAfter(to)) return 0;

        // API argentinadatos (migración 2026-07): el endpoint /historico ahora envuelve el
        // array bajo {"historico": [...]} en vez de devolverlo directamente.
        ArgentinadatosFciHistoricoResponseDto response;
        try {
            response = macroRestTemplate.getForObject(
                    macroBaseUrl + "/finanzas/fci/fondos/" + slugify(fondo) + "/historico",
                    ArgentinadatosFciHistoricoResponseDto.class);
        } catch (Exception e) {
            log.warn("No se pudo obtener histórico FCI para backfill de '{}': {}", fondo, e.getMessage());
            return 0;
        }
        if (response == null || response.historico().isEmpty()) return 0;

        Set<LocalDate> existing = asset.getValuations().stream()
                .map(InvestmentValuation::getValuationDate)
                .collect(Collectors.toCollection(java.util.HashSet::new));

        int created = 0;
        for (ArgentinadatosFciHistoricoDto dto : response.historico()) {
            if (dto.fecha() == null || dto.valorCuotaparte() == null) continue;
            LocalDate fecha;
            try {
                fecha = LocalDate.parse(dto.fecha());
            } catch (Exception ex) {
                continue;
            }
            if (fecha.isBefore(from) || fecha.isAfter(to)) continue;
            if (!existing.add(fecha)) continue; // evita duplicados (existentes o repetidos en la serie)

            asset.getValuations().add(InvestmentValuation.builder()
                    .investmentAsset(asset)
                    .valuationDate(fecha)
                    .pricePerUnit(dto.valorCuotaparte().setScale(4, RoundingMode.HALF_EVEN))
                    .source("ARGENTINADATOS")
                    .build());
            created++;
        }
        log.info("Backfill FCI '{}': {} valuaciones nuevas entre {} y {}", fondo, created, from, to);
        return created;
    }

    /**
     * Normaliza el nombre de un fondo al slug que usa argentinadatos en la ruta del histórico:
     * minúsculas, sin acentos, y cualquier secuencia no alfanumérica reemplazada por un guión.
     * Ej.: "Alpha Pesos - Clase A" → "alpha-pesos-clase-a".
     */
    static String slugify(String name) {
        String sinAcentos = Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return sinAcentos.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
    }

    // ─── Public status ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public long getSnapshotCount() {
        return fciVcpSnapshotRepository.count();
    }

    @Transactional(readOnly = true)
    public LocalDate getLastSyncDate() {
        return fciVcpSnapshotRepository.findLatestSnapshotDate().orElse(null);
    }

    // ─── Public query ─────────────────────────────────────────────────────────

    /**
     * Retorna el VCP de un fondo FCI para una fecha específica desde los snapshots persistidos.
     * Usado por el endpoint GET /market/fci-vcp para que el frontend pueda recuperar precios
     * históricos al registrar valuaciones manuales de activos FCI_CUOTAPARTES.
     *
     * @param fondo nombre exacto del fondo (externalId del activo)
     * @param fecha fecha del snapshot
     * @return FciFundDto envuelto en Optional, o empty si no existe snapshot para esa fecha
     */
    @Transactional(readOnly = true)
    public Optional<FciFundDto> getVcpForDate(String fondo, LocalDate fecha) {
        Optional<FciFundDto> fromSnapshot = fciVcpSnapshotRepository.findByFondoAndFecha(fondo, fecha)
                .map(s -> new FciFundDto(s.getFondo(), s.getCategoria(), s.getVcp(), s.getFecha()));
        if (fromSnapshot.isPresent()) return fromSnapshot;
        // Fallback: no hay snapshot persistido (típico para fechas históricas anteriores al inicio del
        // job diario) → buscar el VCP en la serie histórica de argentinadatos. Así el formulario de
        // creación con fecha de compra pasada obtiene el VCP de ese día.
        return getVcpFromHistorico(fondo, fecha);
    }

    /**
     * Busca el VCP de un fondo para una fecha en la serie histórica de argentinadatos, tomando el
     * cierre exacto o el más reciente anterior o igual a {@code fecha} (cubre fines de semana y
     * feriados). Degrada a empty ante fallo o ausencia de datos.
     */
    private Optional<FciFundDto> getVcpFromHistorico(String fondo, LocalDate fecha) {
        ArgentinadatosFciHistoricoResponseDto response;
        try {
            response = macroRestTemplate.getForObject(
                    macroBaseUrl + "/finanzas/fci/fondos/" + slugify(fondo) + "/historico",
                    ArgentinadatosFciHistoricoResponseDto.class);
        } catch (Exception e) {
            log.warn("No se pudo obtener histórico FCI para VCP de '{}' al {}: {}", fondo, fecha, e.getMessage());
            return Optional.empty();
        }
        if (response == null || response.historico().isEmpty()) return Optional.empty();

        ArgentinadatosFciHistoricoDto best = null;
        LocalDate bestDate = null;
        for (ArgentinadatosFciHistoricoDto dto : response.historico()) {
            if (dto.fecha() == null || dto.valorCuotaparte() == null) continue;
            LocalDate d;
            try {
                d = LocalDate.parse(dto.fecha());
            } catch (Exception ex) {
                continue;
            }
            if (d.isAfter(fecha)) continue; // sólo cierres ≤ fecha pedida
            if (bestDate == null || d.isAfter(bestDate)) {
                bestDate = d;
                best = dto;
            }
        }
        if (best == null) return Optional.empty();

        return Optional.of(new FciFundDto(
                fondo,
                best.categoriaKey(),
                best.valorCuotaparte().setScale(4, RoundingMode.HALF_EVEN),
                bestDate));
    }

    /**
     * Devuelve el catálogo de fondos FCI disponibles del último snapshot,
     * filtrado por categoría. Usado por el frontend para el combobox de externalId.
     */
    @Transactional(readOnly = true)
    public List<FciFundDto> getLatestFciFunds(String categoria) {
        LocalDate snapshotDate = fciVcpSnapshotRepository.findLatestSnapshotDate()
                .orElse(LocalDate.now(ZoneOffset.UTC).minusDays(1));

        return fciVcpSnapshotRepository.findByCategoriaAndFecha(categoria, snapshotDate).stream()
                .map(s -> new FciFundDto(s.getFondo(), s.getCategoria(), s.getVcp(), s.getFecha()))
                .toList();
    }
}
