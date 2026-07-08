package com.vectis.backend.service;

import com.vectis.backend.config.PpiMarketDataClient;
import com.vectis.backend.config.PpiMarketDataClient.PpiBondEstimate;
import com.vectis.backend.domain.entity.InvestmentAsset;
import com.vectis.backend.domain.entity.InvestmentAssetType;
import com.vectis.backend.domain.entity.User;
import com.vectis.backend.dto.InvestmentPaymentResponse;
import com.vectis.backend.exception.InvestmentNotFoundException;
import com.vectis.backend.repository.InvestmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Orquesta la sincronización del calendario de pagos de un BONO/ON desde PPI {@code Bonds/Estimate}.
 *
 * <p>Deliberadamente <b>sin</b> {@code @Transactional} a nivel de clase: la llamada HTTP externa no
 * debe retener una conexión JDBC. Las lecturas de guardia van por {@link InvestmentRepository}
 * (cada una abre su propia transacción efímera de Spring Data), y tanto la persistencia
 * ({@link InvestmentPaymentService#mergeSchedule}, {@code REQUIRES_NEW}) como la relectura final
 * ({@link InvestmentPaymentService#getPayments}, {@code readOnly}) se delegan al bean colaborador
 * {@link InvestmentPaymentService} — al ser invocaciones <i>cross-bean</i> pasan por el proxy de
 * Spring y respetan su propagación transaccional (necesario porque {@code getPayments} lazy-loadea
 * {@code movements} al estimar los montos). Este split reemplaza el antiguo auto-proxy
 * ({@code ObjectProvider<self>}) y garantiza límites transaccionales correctos.
 *
 * <p>Se dispara tras el alta de un BONO/ON auto-track (ver el {@code afterCommit} en
 * {@code InvestmentService#createInvestment}, que difiere la llamada HTTP a después de commitear el
 * asset) y on-demand desde {@code POST /api/investments/{id}/payments/sync}. Degrada con elegancia
 * en cualquier paso (activo no BONO/ON, sin ticker, PPI no configurado o la llamada falla): devuelve
 * el calendario actual sin cambios, nunca lanza por una causa de PPI.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InvestmentPaymentSyncService {

    private final InvestmentRepository        investmentRepository;
    private final PpiMarketDataClient         ppiMarketDataClient;
    private final InvestmentPaymentService    investmentPaymentService;

    public List<InvestmentPaymentResponse> syncSchedule(UUID assetId, User user) {
        InvestmentAsset asset = investmentRepository.findByIdAndUser_Id(assetId, user.getId())
                .orElseThrow(() -> new InvestmentNotFoundException(assetId));

        if (asset.getType() != InvestmentAssetType.BONO && asset.getType() != InvestmentAssetType.ON) {
            log.debug("syncSchedule: activo {} no es BONO/ON — se omite", assetId);
            return investmentPaymentService.getPayments(assetId, user);
        }
        String ticker = asset.getExternalId();
        if (ticker == null || ticker.isBlank()) {
            log.debug("syncSchedule: activo {} sin externalId — se omite", assetId);
            return investmentPaymentService.getPayments(assetId, user);
        }
        if (!ppiMarketDataClient.isConfigured()) {
            log.info("PPI no configurado — se omite syncSchedule para {}", assetId);
            return investmentPaymentService.getPayments(assetId, user);
        }

        Optional<PpiBondEstimate> estimate = ppiMarketDataClient.getBondEstimate(ticker, asset.getPurchaseDate());
        if (estimate.isEmpty()) {
            log.debug("syncSchedule: PPI no devolvió estimate para ticker={}", ticker);
            return investmentPaymentService.getPayments(assetId, user);
        }

        PpiBondEstimate est = estimate.get();
        String currency = PpiMarketDataClient.normalizePpiCurrency(est.abbreviationCurrencyPay())
                .or(() -> PpiMarketDataClient.normalizePpiCurrency(est.issueCurrency()))
                .orElseGet(() -> {
                    log.warn("syncSchedule: moneda de pago PPI no reconocida (pay='{}', issue='{}') para {} — fallback a la currency del activo",
                            est.abbreviationCurrencyPay(), est.issueCurrency(), ticker);
                    return asset.getCurrency();
                });

        try {
            investmentPaymentService.mergeSchedule(assetId, est, currency);
        } catch (Exception e) {
            log.warn("syncSchedule: no se pudo persistir el calendario de {}: {}", assetId, e.getMessage());
        }
        return investmentPaymentService.getPayments(assetId, user);
    }
}
