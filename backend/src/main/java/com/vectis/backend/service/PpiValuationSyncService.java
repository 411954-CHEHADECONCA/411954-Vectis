package com.vectis.backend.service;

import com.vectis.backend.config.PpiMarketDataClient;
import com.vectis.backend.domain.entity.DoctaInstrumentCache;
import com.vectis.backend.domain.entity.InvestmentAsset;
import com.vectis.backend.domain.entity.InvestmentAssetType;
import com.vectis.backend.domain.entity.InvestmentValuation;
import com.vectis.backend.dto.InstrumentDto;
import com.vectis.backend.repository.DoctaInstrumentCacheRepository;
import com.vectis.backend.repository.InvestmentRepository;
import com.vectis.backend.repository.InvestmentValuationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PpiValuationSyncService {

    private static final List<InvestmentAssetType> PPI_TYPES =
            List.of(InvestmentAssetType.LETRA, InvestmentAssetType.BONO, InvestmentAssetType.ON);

    private final PpiMarketDataClient            ppiMarketDataClient;
    private final DoctaInstrumentCacheRepository instrumentCacheRepository;
    private final InvestmentRepository           investmentRepository;
    private final InvestmentValuationRepository  valuationRepository;

    /**
     * Sincroniza precios de cierre para activos LETRA/BONO/ON con auto-tracking activado.
     * L-V a las 21:00 UTC.
     */
    @Scheduled(cron = "0 0 21 * * MON-FRI")
    @Transactional
    public void syncPpiValuations() {
        if (!ppiMarketDataClient.isConfigured()) {
            log.info("PPI no configurado — se omite syncPpiValuations");
            return;
        }

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        List<InvestmentAsset> assets = investmentRepository
                .findAllByTypesAndAutoTrackTrue(PPI_TYPES);

        log.info("Sincronizando valuaciones PPI para {} activos con auto-track activo", assets.size());

        for (InvestmentAsset asset : assets) {
            try {
                String ticker = asset.getExternalId();
                if (ticker == null || ticker.isBlank()) {
                    log.debug("Activo {} no tiene externalId configurado — se omite", asset.getId());
                    continue;
                }

                String ppiType = mapToPpiType(asset.getType());
                if (ppiType == null) {
                    log.debug("Tipo {} no soportado por PPI — se omite", asset.getType());
                    continue;
                }
                // Persistir la valuación con la FECHA REAL del cierre devuelto (no "hoy"): para ON
                // ilíquidos el último cierre puede ser de semanas atrás; usar `today` falsearía el
                // histórico. savePpiValuation deduplica por fecha (existsBy), así que es idempotente.
                ppiMarketDataClient.getPriceForDate(ticker, ppiType, today).ifPresent(dp -> {
                    try {
                        savePpiValuation(asset, dp.date(), dp.price().setScale(4, RoundingMode.HALF_EVEN));
                    } catch (Exception inner) {
                        log.warn("No se pudo guardar valuacion PPI para activo {}: {}",
                                asset.getId(), inner.getMessage());
                    }
                });
            } catch (Exception e) {
                log.warn("Error sync valuacion PPI para activo {}: {}", asset.getId(), e.getMessage());
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void savePpiValuation(InvestmentAsset asset, LocalDate date, BigDecimal price) {
        boolean exists = valuationRepository
                .existsByInvestmentAsset_IdAndValuationDate(asset.getId(), date);
        if (!exists) {
            valuationRepository.save(InvestmentValuation.builder()
                    .investmentAsset(asset)
                    .valuationDate(date)
                    .pricePerUnit(price)
                    .source("PPI")
                    .build());
            log.debug("Valuacion PPI auto-generada para activo {} en {}", asset.getId(), date);
        }
    }

    public boolean isConfigured() {
        return ppiMarketDataClient.isConfigured();
    }

    // ─── Backfill histórico de valuaciones ──────────────────────────────────────

    /**
     * Rellena las valuaciones faltantes de un activo LETRA/BONO/ON con seguimiento automático,
     * trayendo la serie histórica de precios de PPI ({@code MarketData/Search} por rango) desde la
     * fecha de compra hasta hoy y creando una valuación por cada día disponible que aún no exista.
     *
     * <p>Espeja {@code FciValuationSyncService.backfillValuations}: <b>muta</b> la colección
     * {@code asset.getValuations()} (no persiste); el llamador debe guardar el activo dentro de su
     * transacción para que el cascade persista las valuaciones nuevas. Degrada con elegancia: si la
     * API falla o no hay datos, no lanza y retorna 0.
     *
     * @return cantidad de valuaciones nuevas agregadas a la colección del activo.
     */
    public int backfillValuations(InvestmentAsset asset) {
        if (!PPI_TYPES.contains(asset.getType()) || !asset.isAutoTrack()) return 0;
        String ticker = asset.getExternalId();
        if (ticker == null || ticker.isBlank() || asset.getPurchaseDate() == null) return 0;

        String ppiType = mapToPpiType(asset.getType());
        if (ppiType == null) return 0;

        LocalDate from = asset.getPurchaseDate();
        LocalDate to   = LocalDate.now(ZoneOffset.UTC);
        if (from.isAfter(to)) return 0;

        List<PpiMarketDataClient.DatedPrice> serie =
                ppiMarketDataClient.getPriceSeries(ticker, ppiType, from, to);
        if (serie.isEmpty()) return 0;

        Set<LocalDate> existing = asset.getValuations().stream()
                .map(InvestmentValuation::getValuationDate)
                .collect(Collectors.toCollection(HashSet::new));

        int created = 0;
        for (PpiMarketDataClient.DatedPrice dp : serie) {
            if (dp.date() == null || dp.price() == null) continue;
            if (dp.date().isBefore(from) || dp.date().isAfter(to)) continue;
            if (!existing.add(dp.date())) continue; // evita duplicados (existentes o repetidos en la serie)

            asset.getValuations().add(InvestmentValuation.builder()
                    .investmentAsset(asset)
                    .valuationDate(dp.date())
                    .pricePerUnit(dp.price().setScale(4, RoundingMode.HALF_EVEN))
                    .source("PPI")
                    .build());
            created++;
        }
        log.info("Backfill PPI '{}': {} valuaciones nuevas entre {} y {}", ticker, created, from, to);
        return created;
    }

    /**
     * Devuelve el catálogo de instrumentos <b>vigentes</b> (active=true) cacheado por tipo.
     * El catálogo es sembrado via Flyway (V034) y mantenido al día por
     * {@code InstrumentCatalogSyncService} contra PPI (da de baja los delisted, agrega nuevos).
     */
    @Transactional(readOnly = true)
    public List<InstrumentDto> getInstrumentsByType(String tipo) {
        List<DoctaInstrumentCache> cached = tipo != null && !tipo.isBlank()
                ? instrumentCacheRepository.findAllByTipoAndActiveTrueOrderByNombreAsc(tipo)
                : instrumentCacheRepository.findAllByActiveTrueOrderByNombreAsc();

        return cached.stream()
                .map(c -> new InstrumentDto(c.getTicker(), c.getNombre(), c.getTipo(),
                        c.getLastPrice(), c.getPriceDate(), c.getMaturityDate()))
                .toList();
    }

    private static String mapToPpiType(InvestmentAssetType type) {
        return switch (type) {
            case BONO  -> "BONOS";
            case LETRA -> "LETRAS";
            case ON    -> "ON";
            default    -> null;
        };
    }
}
