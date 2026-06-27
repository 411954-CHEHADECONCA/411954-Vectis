package com.vectis.backend.service;

import com.vectis.backend.domain.entity.FciVcpSnapshot;
import com.vectis.backend.domain.entity.InvestmentAsset;
import com.vectis.backend.domain.entity.InvestmentAssetType;
import com.vectis.backend.domain.entity.InvestmentValuation;
import com.vectis.backend.dto.FciFundDto;
import com.vectis.backend.dto.macro.ArgentinadatosFciDto;
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
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

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

    private static final String[] FCI_CATEGORIAS = {"mercadoDinero", "rentaFija", "rentaVariable"};

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
        return fciVcpSnapshotRepository.findByFondoAndFecha(fondo, fecha)
                .map(s -> new FciFundDto(s.getFondo(), s.getCategoria(), s.getVcp(), s.getFecha()));
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
