package com.vectis.backend.service;

import com.vectis.backend.config.PpiMarketDataClient;
import com.vectis.backend.domain.entity.InvestmentAsset;
import com.vectis.backend.dto.MarketRefreshResponse;
import com.vectis.backend.dto.MarketSourceRefreshResult;
import com.vectis.backend.repository.ExchangeRateRepository;
import com.vectis.backend.repository.FciVcpSnapshotRepository;
import com.vectis.backend.repository.InvestmentRepository;
import com.vectis.backend.repository.InvestmentValuationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Orquesta el refresh on-demand de las cuatro fuentes de datos de mercado externas
 * (MEP, oficial, FCI, PPI), reutilizando los métodos públicos de sincronización ya
 * existentes en {@link MacroDataService}, {@link FciValuationSyncService} y
 * {@link PpiValuationSyncService}. No duplica lógica de fetch: sólo decide *cuándo*
 * invocarlos (según frescura o {@code force}) y arma un resumen por fuente.
 *
 * <p>Cada fuente se procesa en su propio try/catch: si una falla, las demás continúan.
 * La frescura se determina re-consultando el repositorio correspondiente después de
 * invocar el sync — así el resultado ("refreshed" vs "failed") refleja el estado real
 * de los datos, sin depender de que los servicios de sync propaguen excepciones (varios
 * las atrapan internamente y sólo loguean un warning).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketDataRefreshService {

    private static final String RATE_TYPE_MEP     = "MEP";
    private static final String RATE_TYPE_OFICIAL = "OFICIAL";
    private static final String SOURCE_PPI        = "PPI";

    private final MacroDataService              macroDataService;
    private final FciValuationSyncService       fciValuationSyncService;
    private final PpiValuationSyncService       ppiValuationSyncService;
    private final ExchangeRateRepository        exchangeRateRepository;
    private final FciVcpSnapshotRepository      fciVcpSnapshotRepository;
    private final InvestmentValuationRepository investmentValuationRepository;
    private final InvestmentRepository          investmentRepository;
    private final PpiMarketDataClient           ppiMarketDataClient;

    /**
     * Exclusión mutua entre refresh concurrentes (trigger silencioso del shell, botón manual
     * y cron de las 21:00 UTC): si ya hay uno en curso, se responde "alreadyRunning" sin
     * volver a golpear las APIs externas. Mismo patrón que InstrumentCatalogSyncService.
     */
    private final AtomicBoolean refreshInProgress = new AtomicBoolean(false);

    // Sin @Transactional: este orquestador hace llamadas HTTP externas (PPI, argentinadatos,
    // dolarapi) que pueden tardar varios segundos; una transacción abierta acá retendría la
    // conexión JDBC todo ese tiempo. La persistencia ocurre en transacciones cortas dentro de
    // los servicios de sync.
    public MarketRefreshResponse refresh(boolean force) {
        if (!refreshInProgress.compareAndSet(false, true)) {
            log.info("Refresh de mercado ya en curso — se responde alreadyRunning");
            return alreadyRunningResponse();
        }
        try {
            LocalDate lastBusinessDay = lastBusinessDay(LocalDate.now(ZoneOffset.UTC));

            List<MarketSourceRefreshResult> sources = new ArrayList<>();
            sources.add(refreshMep(force, lastBusinessDay));
            sources.add(refreshOficial(force, lastBusinessDay));
            sources.add(refreshFci(force, lastBusinessDay));
            sources.add(refreshPpi(force, lastBusinessDay));

            return new MarketRefreshResponse(sources);
        } finally {
            refreshInProgress.set(false);
        }
    }

    private MarketRefreshResponse alreadyRunningResponse() {
        return new MarketRefreshResponse(List.of(
                new MarketSourceRefreshResult("mep",     "alreadyRunning", macroDataService.getLatestMepDate()),
                new MarketSourceRefreshResult("oficial", "alreadyRunning", macroDataService.getLatestOficialDate()),
                new MarketSourceRefreshResult("fci",     "alreadyRunning", fciVcpSnapshotRepository.findLatestSnapshotDate().orElse(null)),
                new MarketSourceRefreshResult("ppi",     "alreadyRunning", ppiValuationSyncService.getLastSyncDate())));
    }

    // ─── MEP ──────────────────────────────────────────────────────────────────

    private MarketSourceRefreshResult refreshMep(boolean force, LocalDate lastBusinessDay) {
        boolean fresh = exchangeRateRepository.existsByRateTypeAndRateDate(RATE_TYPE_MEP, lastBusinessDay);
        if (!force && fresh) {
            return new MarketSourceRefreshResult("mep", "upToDate", macroDataService.getLatestMepDate());
        }
        try {
            macroDataService.fetchLatestMepRate();
        } catch (Exception e) {
            log.warn("Refresh on-demand de MEP falló: {}", e.getMessage());
        }
        boolean nowFresh = exchangeRateRepository.existsByRateTypeAndRateDate(RATE_TYPE_MEP, lastBusinessDay);
        String status = nowFresh ? "refreshed" : "failed";
        return new MarketSourceRefreshResult("mep", status, macroDataService.getLatestMepDate());
    }

    // ─── Oficial ──────────────────────────────────────────────────────────────

    private MarketSourceRefreshResult refreshOficial(boolean force, LocalDate lastBusinessDay) {
        boolean fresh = exchangeRateRepository.existsByRateTypeAndRateDate(RATE_TYPE_OFICIAL, lastBusinessDay);
        if (!force && fresh) {
            return new MarketSourceRefreshResult("oficial", "upToDate", macroDataService.getLatestOficialDate());
        }
        try {
            macroDataService.fetchOficialRate();
        } catch (Exception e) {
            log.warn("Refresh on-demand de oficial falló: {}", e.getMessage());
        }
        boolean nowFresh = exchangeRateRepository.existsByRateTypeAndRateDate(RATE_TYPE_OFICIAL, lastBusinessDay);
        String status = nowFresh ? "refreshed" : "failed";
        return new MarketSourceRefreshResult("oficial", status, macroDataService.getLatestOficialDate());
    }

    // ─── FCI ──────────────────────────────────────────────────────────────────

    private MarketSourceRefreshResult refreshFci(boolean force, LocalDate lastBusinessDay) {
        LocalDate latestSnapshot = fciVcpSnapshotRepository.findLatestSnapshotDate().orElse(null);
        boolean fresh = latestSnapshot != null && !latestSnapshot.isBefore(lastBusinessDay);
        if (!force && fresh) {
            return new MarketSourceRefreshResult("fci", "upToDate", latestSnapshot);
        }
        try {
            fciValuationSyncService.fetchFciVcp();
            fciValuationSyncService.syncFciValuations();
        } catch (Exception e) {
            log.warn("Refresh on-demand de FCI falló: {}", e.getMessage());
        }
        LocalDate newLatest = fciVcpSnapshotRepository.findLatestSnapshotDate().orElse(null);
        boolean nowFresh = newLatest != null && !newLatest.isBefore(lastBusinessDay);
        String status = nowFresh ? "refreshed" : "failed";
        return new MarketSourceRefreshResult("fci", status, newLatest);
    }

    // ─── PPI ──────────────────────────────────────────────────────────────────

    private MarketSourceRefreshResult refreshPpi(boolean force, LocalDate lastBusinessDay) {
        if (!ppiMarketDataClient.isConfigured()) {
            return new MarketSourceRefreshResult("ppi", "notConfigured", null);
        }

        List<InvestmentAsset> ppiAssets = investmentRepository
                .findAllByTypesAndAutoTrackTrue(PpiValuationSyncService.PPI_TYPES);
        if (ppiAssets.isEmpty()) {
            return new MarketSourceRefreshResult("ppi", "upToDate", ppiValuationSyncService.getLastSyncDate());
        }

        boolean fresh = investmentValuationRepository.existsBySourceAndValuationDate(SOURCE_PPI, lastBusinessDay);
        if (!force && fresh) {
            return new MarketSourceRefreshResult("ppi", "upToDate", ppiValuationSyncService.getLastSyncDate());
        }
        try {
            ppiValuationSyncService.syncPpiValuations();
        } catch (Exception e) {
            log.warn("Refresh on-demand de PPI falló: {}", e.getMessage());
        }
        boolean nowFresh = investmentValuationRepository.existsBySourceAndValuationDate(SOURCE_PPI, lastBusinessDay);
        String status = nowFresh ? "refreshed" : "failed";
        return new MarketSourceRefreshResult("ppi", status, ppiValuationSyncService.getLastSyncDate());
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /** Devuelve el último día hábil: hoy si es de lunes a viernes, o el viernes anterior si cae en fin de semana. */
    static LocalDate lastBusinessDay(LocalDate reference) {
        DayOfWeek dow = reference.getDayOfWeek();
        return switch (dow) {
            case SATURDAY -> reference.minusDays(1);
            case SUNDAY   -> reference.minusDays(2);
            default       -> reference;
        };
    }
}
