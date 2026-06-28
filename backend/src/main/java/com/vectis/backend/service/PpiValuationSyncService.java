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
import java.util.List;

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
                ppiMarketDataClient.getPriceForDate(ticker, ppiType, today).ifPresent(price -> {
                    try {
                        savePpiValuation(asset, today, price.setScale(4, RoundingMode.HALF_EVEN));
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

    /**
     * Devuelve el catálogo de instrumentos cacheado por tipo.
     * El catálogo es sembrado via Flyway (V034) y se actualiza con precios de PPI
     * cada vez que se ejecuta syncPpiValuations() para activos con auto-track.
     */
    @Transactional(readOnly = true)
    public List<InstrumentDto> getInstrumentsByType(String tipo) {
        List<DoctaInstrumentCache> cached = tipo != null && !tipo.isBlank()
                ? instrumentCacheRepository.findAllByTipoOrderByNombreAsc(tipo)
                : instrumentCacheRepository.findAll();

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
