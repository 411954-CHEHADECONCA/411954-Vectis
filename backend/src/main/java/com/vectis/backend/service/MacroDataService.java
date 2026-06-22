package com.vectis.backend.service;

import com.vectis.backend.domain.entity.ExchangeRate;
import com.vectis.backend.domain.entity.InflationRecord;
import com.vectis.backend.dto.ExchangeRateResponse;
import com.vectis.backend.dto.InflationResponse;
import com.vectis.backend.dto.macro.ArgentinadatosInflacionDto;
import com.vectis.backend.dto.macro.ArgentinadatosRateDto;
import com.vectis.backend.dto.macro.DolarApiRateDto;
import com.vectis.backend.exception.VectisException;
import com.vectis.backend.repository.ExchangeRateRepository;
import com.vectis.backend.repository.InflationRecordRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;

@Slf4j
@Service
public class MacroDataService {

    private final RestTemplate macroRestTemplate;
    private final ExchangeRateRepository exchangeRateRepository;
    private final InflationRecordRepository inflationRecordRepository;

    @Value("${macro.api.base-url}")
    private String macroBaseUrl;

    @Value("${dolar.api.base-url}")
    private String dolarBaseUrl;

    private static final LocalDate BACKFILL_FROM  = LocalDate.of(2026, 1, 1);
    private static final String    RATE_TYPE_MEP   = "MEP";
    private static final String    RATE_TYPE_OFICIAL = "OFICIAL";

    public MacroDataService(
            @Qualifier("macroRestTemplate") RestTemplate macroRestTemplate,
            ExchangeRateRepository exchangeRateRepository,
            InflationRecordRepository inflationRecordRepository) {
        this.macroRestTemplate         = macroRestTemplate;
        this.exchangeRateRepository    = exchangeRateRepository;
        this.inflationRecordRepository = inflationRecordRepository;
    }

    // ─── Backfill inicial ─────────────────────────────────────────────────────

    @EventListener(ApplicationReadyEvent.class)
    public void initialBackfill() {
        try {
            backfillMepRates();
            backfillInflationRecords();
            fetchOficialRate();   // dolarapi.com solo tiene el valor actual
        } catch (Exception e) {
            log.warn("Backfill inicial fallo (no critico): {}", e.getMessage(), e);
        }
    }

    public void backfillMepRates() {
        ArgentinadatosRateDto[] rates = macroRestTemplate.getForObject(
                macroBaseUrl + "/cotizaciones/dolares/bolsa", ArgentinadatosRateDto[].class);
        if (rates == null) return;
        for (ArgentinadatosRateDto dto : rates) {
            LocalDate date = LocalDate.parse(dto.fecha());
            if (!date.isBefore(BACKFILL_FROM)
                    && !exchangeRateRepository.existsByRateTypeAndRateDate(RATE_TYPE_MEP, date)) {
                saveMepRate(dto, date);
            }
        }
        log.info("Backfill MEP completado");
    }

    public void backfillInflationRecords() {
        ArgentinadatosInflacionDto[] records = macroRestTemplate.getForObject(
                macroBaseUrl + "/finanzas/indices/inflacion", ArgentinadatosInflacionDto[].class);
        if (records == null) return;
        for (ArgentinadatosInflacionDto r : records) {
            LocalDate date = LocalDate.parse(r.fecha());
            if (!date.isBefore(BACKFILL_FROM)
                    && !inflationRecordRepository.existsByPeriodDate(date)) {
                saveInflationRecord(r, date);
            }
        }
        log.info("Backfill IPC completado");
    }

    // ─── Schedulers ──────────────────────────────────────────────────────────

    /** Cada dia de lunes a viernes a las 20:30 UTC (cierre del mercado MEP). */
    @Scheduled(cron = "0 30 20 * * MON-FRI")
    public void fetchLatestMepRate() {
        try {
            ArgentinadatosRateDto[] rates = macroRestTemplate.getForObject(
                    macroBaseUrl + "/cotizaciones/dolares/bolsa", ArgentinadatosRateDto[].class);
            if (rates == null || rates.length == 0) return;
            ArgentinadatosRateDto latest = rates[rates.length - 1];
            LocalDate date = LocalDate.parse(latest.fecha());
            if (!exchangeRateRepository.existsByRateTypeAndRateDate(RATE_TYPE_MEP, date)) {
                saveMepRate(latest, date);
            }
        } catch (Exception e) {
            log.warn("No se pudo obtener cotizacion MEP: {}", e.getMessage());
        }
    }

    /** Cada hora de lunes a viernes; dolarapi.com actualiza cada ~15 min. */
    @Scheduled(cron = "0 0 * * * MON-FRI")
    public void fetchOficialRate() {
        try {
            DolarApiRateDto[] rates = macroRestTemplate.getForObject(
                    dolarBaseUrl + "/dolares", DolarApiRateDto[].class);
            if (rates == null) return;
            Arrays.stream(rates)
                    .filter(r -> "oficial".equalsIgnoreCase(r.casa()))
                    .findFirst()
                    .ifPresent(this::upsertOficialRate);
        } catch (Exception e) {
            log.warn("No se pudo obtener cotizacion oficial: {}", e.getMessage());
        }
    }

    /** El dia 5 de cada mes a las 13:00 UTC (publicacion del IPC). */
    @Scheduled(cron = "0 0 13 5 * *")
    public void fetchLatestInflationIndex() {
        try {
            ArgentinadatosInflacionDto[] records = macroRestTemplate.getForObject(
                    macroBaseUrl + "/finanzas/indices/inflacion", ArgentinadatosInflacionDto[].class);
            if (records == null) return;
            for (ArgentinadatosInflacionDto r : records) {
                LocalDate date = LocalDate.parse(r.fecha());
                if (!inflationRecordRepository.existsByPeriodDate(date)) {
                    saveInflationRecord(r, date);
                }
            }
        } catch (Exception e) {
            log.warn("No se pudo obtener IPC: {}", e.getMessage());
        }
    }

    // ─── Queries publicas ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ExchangeRateResponse getLatestMepRate() {
        return exchangeRateRepository.findTopByRateTypeOrderByRateDateDesc(RATE_TYPE_MEP)
                .map(this::toResponse)
                .orElseThrow(() -> new VectisException("Cotizacion MEP no disponible", HttpStatus.NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public ExchangeRateResponse getLatestOficialRate() {
        return exchangeRateRepository.findTopByRateTypeOrderByRateDateDesc(RATE_TYPE_OFICIAL)
                .map(this::toResponse)
                .orElseThrow(() -> new VectisException("Cotizacion oficial no disponible", HttpStatus.NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public InflationResponse getLatestInflation() {
        return inflationRecordRepository.findTopByOrderByPeriodDateDesc()
                .map(r -> new InflationResponse(
                        r.getMonthlyRate().toPlainString(),
                        r.getPeriodDate().toString(),
                        r.getSource()))
                .orElseThrow(() -> new VectisException("IPC no disponible", HttpStatus.NOT_FOUND));
    }

    // ─── Helpers privados ─────────────────────────────────────────────────────

    private void saveMepRate(ArgentinadatosRateDto dto, LocalDate date) {
        exchangeRateRepository.save(ExchangeRate.builder()
                .rateType(RATE_TYPE_MEP)
                .buy(dto.compra().setScale(4, RoundingMode.HALF_EVEN))
                .sell(dto.venta().setScale(4, RoundingMode.HALF_EVEN))
                .rateDate(date)
                .build());
    }

    private void upsertOficialRate(DolarApiRateDto dto) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        exchangeRateRepository.findByRateTypeAndRateDate(RATE_TYPE_OFICIAL, today)
                .ifPresentOrElse(
                        existing -> {
                            existing.setBuy(dto.compra().setScale(4, RoundingMode.HALF_EVEN));
                            existing.setSell(dto.venta().setScale(4, RoundingMode.HALF_EVEN));
                            exchangeRateRepository.save(existing);
                        },
                        () -> exchangeRateRepository.save(ExchangeRate.builder()
                                .rateType(RATE_TYPE_OFICIAL)
                                .buy(dto.compra().setScale(4, RoundingMode.HALF_EVEN))
                                .sell(dto.venta().setScale(4, RoundingMode.HALF_EVEN))
                                .rateDate(today)
                                .build())
                );
    }

    private void saveInflationRecord(ArgentinadatosInflacionDto dto, LocalDate date) {
        inflationRecordRepository.save(InflationRecord.builder()
                .periodDate(date)
                .monthlyRate(dto.valor().setScale(4, RoundingMode.HALF_EVEN))
                .build());
    }

    private ExchangeRateResponse toResponse(ExchangeRate r) {
        return new ExchangeRateResponse(
                r.getRateType(),
                r.getBuy().toPlainString(),
                r.getSell().toPlainString(),
                r.getRateDate().toString(),
                r.getSource());
    }
}
