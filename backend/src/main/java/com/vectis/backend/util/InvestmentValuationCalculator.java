package com.vectis.backend.util;

import com.vectis.backend.domain.entity.InvestmentAsset;
import com.vectis.backend.domain.entity.InvestmentAssetType;
import com.vectis.backend.domain.entity.InvestmentMovementType;
import com.vectis.backend.domain.entity.InvestmentValuation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.Optional;

/**
 * Cálculo del valor de mercado actual de un activo de inversión — única fuente de verdad usada tanto
 * por {@code InvestmentService#collectInvestment} (monto a acreditar al cobrar la inversión) como por
 * {@code InvestmentMapper} (campo {@code currentValue} expuesto en {@code InvestmentResponse}), para
 * que ambos no terminen calculando el mismo número por caminos distintos y divergiendo con el tiempo.
 *
 * <p>Réplica del cálculo ya existente en el frontend ({@code calcValorActualCP} en
 * {@code inversiones.component.ts}).
 *
 * <p>Sin estado y sin dependencias — clase de utilidad con métodos estáticos, no un bean de Spring.
 */
public final class InvestmentValuationCalculator {

    private static final int MONEY_SCALE = 4;

    private InvestmentValuationCalculator() {
    }

    /** Tipos cuyo rendimiento deriva de valuaciones/precio de mercado (no de TNA). */
    public static boolean isCuotaparteFamily(InvestmentAssetType type) {
        return type == InvestmentAssetType.FCI_CUOTAPARTES
                || type == InvestmentAssetType.LETRA
                || type == InvestmentAssetType.BONO
                || type == InvestmentAssetType.ON;
    }

    /** Nominales/cuotapartes netos en tenencia: suma de unidades SUSCRIPCION menos RESCATE. */
    public static BigDecimal calcHeldUnits(InvestmentAsset asset) {
        return asset.getMovements().stream()
                .filter(m -> m.getUnits() != null)
                .map(m -> m.getType() == InvestmentMovementType.SUSCRIPCION
                        ? m.getUnits()
                        : m.getUnits().negate())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Valor de mercado actual del activo.
     *
     * <p>FCI (Cuenta Remunerada): {@code principal} ya capitaliza los REVALUO, así que ya es el valor
     * actual. PLAZO_FIJO: {@code principal} es fijo/definido por el usuario, no hay valuación de
     * mercado que aplicar.
     *
     * <p>Familia cuotapartes (FCI_CUOTAPARTES/LETRA/BONO/ON): {@code principal} sólo refleja el costo
     * de adquisición (SUSCRIPCION − RESCATE); el valor real es la tenencia (nominales/cuotapartes) ×
     * el último {@code pricePerUnit} registrado.
     *
     * <p>Si el activo no tiene movimientos registrados (sin unidades para valuar) se usa el principal
     * tal cual, como mejor aproximación disponible. Si hay movimientos pero todavía no hay ninguna
     * valuación cargada: LETRA (cero cupón, VN=1 por nominal) usa la tenencia como valor de rescate;
     * el resto cae también al principal (costo de adquisición) por falta de precio de mercado.
     */
    public static BigDecimal calculateCurrentValue(InvestmentAsset asset) {
        if (!isCuotaparteFamily(asset.getType())) {
            return asset.getPrincipal();
        }
        if (asset.getMovements().isEmpty()) {
            return asset.getPrincipal();
        }

        BigDecimal held = calcHeldUnits(asset);
        Optional<InvestmentValuation> latest = asset.getValuations().stream()
                .max(Comparator.comparing(InvestmentValuation::getValuationDate));

        if (latest.isEmpty()) {
            if (asset.getType() == InvestmentAssetType.LETRA) {
                return held.setScale(MONEY_SCALE, RoundingMode.HALF_EVEN);
            }
            return asset.getPrincipal();
        }

        return held.multiply(latest.get().getPricePerUnit()).setScale(MONEY_SCALE, RoundingMode.HALF_EVEN);
    }
}
