package com.vectis.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Schema(description = "Estado de flujo de caja mensual del usuario")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CashflowResponse {

    @Schema(description = "Año del período", example = "2025")
    private int year;

    @Schema(description = "Mes del período (1-12)", example = "6")
    private int month;

    @Schema(description = "Etiqueta larga del período", example = "Junio 2025")
    private String periodLabel;

    @Schema(description = "Abreviatura del mes", example = "jun")
    private String monthShort;

    @Schema(description = "Estado del período", example = "curso",
            allowableValues = {"cerrado", "curso", "abierto", "proyectado"})
    private String status;

    @JsonProperty("isProjection")
    @Schema(description = "Indica si los datos son una proyección basada en recurrentes y presupuesto", example = "false")
    private boolean isProjection;

    @Schema(description = "Indica si los movimientos recurrentes ya fueron materializados en este mes", example = "true")
    private boolean recurringMaterialized;

    @Schema(description = "Saldo de apertura (último día del mes anterior)")
    private CashflowBalanceSection openingBalance;

    @Schema(description = "Ingresos del período agrupados por categoría")
    private CashflowFlowSection income;

    @Schema(description = "Egresos del período agrupados por categoría")
    private CashflowFlowSection expenses;

    @Schema(description = "Subtotal previo a inversiones")
    private CashflowSubtotal preInvestmentBalance;

    @Schema(description = "Inversiones del período")
    private CashflowInvestmentSection investments;

    @Schema(description = "Saldo de cierre (último día del mes o hoy si es el mes actual)")
    private CashflowBalanceSection closingBalance;

    @Schema(description = "Indica si el balance pre-inversión es negativo (déficit de liquidez)", example = "false")
    private boolean hasLiquidityDeficit;

    @Schema(description = "Monto absoluto del déficit de liquidez en formato string", example = "0.0000")
    private String liquidityDeficit;

    @Schema(description = "Indica si el mes futuro aún no tiene una proyección confirmada", example = "false")
    private boolean needsConfirmation;

    @Schema(description = "Cotizacion OFICIAL (venta) al cierre del periodo. Null si no hay datos disponibles.", example = "1062.5000")
    private String oficialRateAtPeriod;

    @Schema(description = "Año del mes más antiguo navegable hacia atrás en el cashflow", example = "2025")
    private int earliestNavigableYear;

    @Schema(description = "Mes (1-12) más antiguo navegable hacia atrás en el cashflow", example = "3")
    private int earliestNavigableMonth;
}
