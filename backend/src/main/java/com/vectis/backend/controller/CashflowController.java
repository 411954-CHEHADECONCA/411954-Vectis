package com.vectis.backend.controller;

import com.vectis.backend.domain.entity.User;
import com.vectis.backend.dto.CashflowResponse;
import com.vectis.backend.exception.GlobalExceptionHandler.ErrorResponse;
import com.vectis.backend.service.CashflowService;
import com.vectis.backend.service.MonthPeriodService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@Validated
@RestController
@RequestMapping("/api/cashflow")
@RequiredArgsConstructor
@Tag(name = "Cashflow", description = "Flujo de caja mensual del usuario")
@SecurityRequirement(name = "BearerAuth")
public class CashflowController {

    private final CashflowService cashflowService;
    private final MonthPeriodService monthPeriodService;

    @GetMapping
    @Operation(summary = "Obtiene el estado de flujo de caja del mes",
               description = "Devuelve el cashflow mensual con saldos de apertura/cierre, ingresos, egresos e inversiones. " +
                             "Si no se indican año y mes, usa el mes actual. " +
                             "Para meses futuros los datos son una proyección basada en recurrentes y presupuesto.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Cashflow calculado correctamente",
            content = @Content(schema = @Schema(implementation = CashflowResponse.class))),
        @ApiResponse(responseCode = "400", description = "Parámetros year o month fuera de rango",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Token JWT ausente o inválido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<CashflowResponse> getCashflow(
            @RequestParam(required = false) @Min(1900) @Max(2200) Integer year,
            @RequestParam(required = false) @Min(1) @Max(12) Integer month,
            @AuthenticationPrincipal User user) {

        LocalDate today = LocalDate.now();
        int y = year  != null ? year  : today.getYear();
        int m = month != null ? month : today.getMonthValue();

        return ResponseEntity.ok(cashflowService.getCashflow(user, y, m));
    }

    @PostMapping("/{year}/{month}/close")
    @Operation(summary = "Cierra un mes para registro de movimientos")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Mes cerrado correctamente",
            content = @Content(schema = @Schema(implementation = CashflowResponse.class))),
        @ApiResponse(responseCode = "401", description = "No autenticado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<CashflowResponse> closePeriod(
            @AuthenticationPrincipal User user,
            @PathVariable int year,
            @PathVariable int month) {
        monthPeriodService.closePeriod(user, year, month);
        return ResponseEntity.ok(cashflowService.getCashflow(user, year, month));
    }

    @PostMapping("/{year}/{month}/open")
    @Operation(summary = "Abre o reabre un mes para registro de movimientos")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Mes abierto correctamente",
            content = @Content(schema = @Schema(implementation = CashflowResponse.class))),
        @ApiResponse(responseCode = "401", description = "No autenticado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<CashflowResponse> openPeriod(
            @AuthenticationPrincipal User user,
            @PathVariable int year,
            @PathVariable int month) {
        monthPeriodService.openPeriod(user, year, month);
        return ResponseEntity.ok(cashflowService.getCashflow(user, year, month));
    }

    @Operation(summary = "Confirmar proyección de un mes futuro")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Proyección confirmada",
            content = @Content(schema = @Schema(implementation = CashflowResponse.class))),
        @ApiResponse(responseCode = "400", description = "El mes no es futuro",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "No autenticado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @SecurityRequirement(name = "BearerAuth")
    @PostMapping("/{year}/{month}/project")
    public ResponseEntity<CashflowResponse> confirmProjection(
            @PathVariable int year,
            @PathVariable int month,
            @AuthenticationPrincipal User currentUser) {
        try {
            monthPeriodService.confirmProjection(currentUser, year, month);
            CashflowResponse response = cashflowService.getCashflow(currentUser, year, month);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
