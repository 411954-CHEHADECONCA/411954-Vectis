package com.vectis.backend.controller;

import com.vectis.backend.domain.entity.User;
import com.vectis.backend.dto.ConfirmPaymentRequest;
import com.vectis.backend.dto.ConfirmPaymentResponse;
import com.vectis.backend.dto.InvestmentPaymentRequest;
import com.vectis.backend.dto.InvestmentPaymentResponse;
import com.vectis.backend.dto.InvestmentPaymentUpdateRequest;
import com.vectis.backend.dto.PendingPaymentResponse;
import com.vectis.backend.exception.GlobalExceptionHandler.ErrorResponse;
import com.vectis.backend.service.InvestmentPaymentService;
import com.vectis.backend.service.InvestmentPaymentSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/investments")
@RequiredArgsConstructor
@Tag(name = "Investment Payments", description = "Calendario de pagos (renta/amortización) de BONO/ON")
@SecurityRequirement(name = "BearerAuth")
public class InvestmentPaymentController {

    private final InvestmentPaymentService investmentPaymentService;
    private final InvestmentPaymentSyncService investmentPaymentSyncService;

    @GetMapping("/{id}/payments")
    @Operation(summary = "Listar el calendario de pagos de un activo BONO/ON")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Calendario de pagos",
            content = @Content(mediaType = "application/json",
                array = @ArraySchema(schema = @Schema(implementation = InvestmentPaymentResponse.class)))),
        @ApiResponse(responseCode = "401", description = "Token JWT ausente o inválido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Activo no encontrado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Error interno del servidor",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public List<InvestmentPaymentResponse> getPayments(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user) {
        return investmentPaymentService.getPayments(id, user);
    }

    @PostMapping("/{id}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Dar de alta manualmente un pago del calendario",
               description = "Crea un pago con fuente MANUAL y montos absolutos (no factores por 100). "
                       + "Sólo aplica a activos BONO/ON.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Pago creado",
            content = @Content(schema = @Schema(implementation = InvestmentPaymentResponse.class))),
        @ApiResponse(responseCode = "400", description = "Datos de entrada inválidos",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Token JWT ausente o inválido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Activo no encontrado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "El activo no es BONO/ON, o ya existe un pago manual "
                + "para esa fecha",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Error interno del servidor",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public InvestmentPaymentResponse createPayment(
            @PathVariable UUID id,
            @Valid @RequestBody InvestmentPaymentRequest request,
            @AuthenticationPrincipal User user) {
        return investmentPaymentService.createManualPayment(id, request, user);
    }

    @PutMapping("/{id}/payments/{paymentId}")
    @Operation(summary = "Editar un pago del calendario (sólo PENDIENTE)",
               description = "Todos los campos son opcionales: sólo se actualiza lo que venga presente.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Pago actualizado",
            content = @Content(schema = @Schema(implementation = InvestmentPaymentResponse.class))),
        @ApiResponse(responseCode = "400", description = "Datos de entrada inválidos",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Token JWT ausente o inválido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Activo o pago no encontrado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "El pago no está pendiente",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Error interno del servidor",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public InvestmentPaymentResponse updatePayment(
            @PathVariable UUID id,
            @PathVariable UUID paymentId,
            @Valid @RequestBody InvestmentPaymentUpdateRequest request,
            @AuthenticationPrincipal User user) {
        return investmentPaymentService.updatePayment(id, paymentId, request, user);
    }

    @DeleteMapping("/{id}/payments/{paymentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Eliminar un pago manual pendiente del calendario",
               description = "Sólo permitido para pagos con fuente MANUAL y estado PENDIENTE.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Pago eliminado"),
        @ApiResponse(responseCode = "401", description = "Token JWT ausente o inválido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Activo o pago no encontrado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "El pago no es manual o no está pendiente",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Error interno del servidor",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public void deletePayment(
            @PathVariable UUID id,
            @PathVariable UUID paymentId,
            @AuthenticationPrincipal User user) {
        investmentPaymentService.deletePayment(id, paymentId, user);
    }

    @PostMapping("/{id}/payments/{paymentId}/omit")
    @Operation(summary = "Marcar un pago pendiente como OMITIDO")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Pago marcado OMITIDO",
            content = @Content(schema = @Schema(implementation = InvestmentPaymentResponse.class))),
        @ApiResponse(responseCode = "401", description = "Token JWT ausente o inválido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Activo o pago no encontrado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "El pago no está pendiente",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Error interno del servidor",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public InvestmentPaymentResponse omitPayment(
            @PathVariable UUID id,
            @PathVariable UUID paymentId,
            @AuthenticationPrincipal User user) {
        return investmentPaymentService.omitPayment(id, paymentId, user);
    }

    @PostMapping("/{id}/payments/{paymentId}/confirm")
    @Operation(summary = "Confirmar el cobro de un pago del calendario",
               description = "Genera hasta dos transacciones (renta y/o amortización) en la cuenta de cobro del "
                       + "activo y marca el pago COBRADO. Si es el pago final (residual 0) y no quedan más pagos "
                       + "pendientes, el activo pasa automáticamente a COBRADA.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Pago confirmado",
            content = @Content(schema = @Schema(implementation = ConfirmPaymentResponse.class))),
        @ApiResponse(responseCode = "400", description = "Datos de entrada inválidos",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Token JWT ausente o inválido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Activo o pago no encontrado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "El activo no está activo, el pago no está pendiente, "
                + "el mes elegido ya está cerrado, o la moneda de la cuenta de cobro no coincide",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Error interno del servidor",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ConfirmPaymentResponse confirmPayment(
            @PathVariable UUID id,
            @PathVariable UUID paymentId,
            @Valid @RequestBody ConfirmPaymentRequest request,
            @AuthenticationPrincipal User user) {
        return investmentPaymentService.confirmPayment(id, paymentId, request, user);
    }

    @PostMapping("/{id}/payments/{paymentId}/revert")
    @Operation(summary = "Revertir un pago cobrado a PENDIENTE",
               description = "Soft-deletea las transacciones generadas al confirmar. Bloqueado si el mes del "
                       + "cobro ya cerró o si el activo ya está COBRADA.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Pago revertido a PENDIENTE",
            content = @Content(schema = @Schema(implementation = InvestmentPaymentResponse.class))),
        @ApiResponse(responseCode = "401", description = "Token JWT ausente o inválido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Activo o pago no encontrado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "El pago no está cobrado, el activo ya fue cobrado en "
                + "su totalidad, o el mes del cobro ya está cerrado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Error interno del servidor",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public InvestmentPaymentResponse revertPayment(
            @PathVariable UUID id,
            @PathVariable UUID paymentId,
            @AuthenticationPrincipal User user) {
        return investmentPaymentService.revertPayment(id, paymentId, user);
    }

    @PostMapping("/{id}/payments/sync")
    @Operation(summary = "Sincronizar el calendario de pagos desde PPI (Bonds/Estimate)",
               description = "Siempre devuelve 200 con el calendario actual, aunque PPI esté caído o no "
                       + "configurado (degrada sin error). Merge idempotente: nunca pisa pagos ya editados por "
                       + "el usuario o con estado distinto de PENDIENTE.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Calendario sincronizado (o sin cambios si PPI degrada)",
            content = @Content(mediaType = "application/json",
                array = @ArraySchema(schema = @Schema(implementation = InvestmentPaymentResponse.class)))),
        @ApiResponse(responseCode = "401", description = "Token JWT ausente o inválido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Activo no encontrado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Error interno del servidor",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public List<InvestmentPaymentResponse> syncPayments(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user) {
        return investmentPaymentSyncService.syncSchedule(id, user);
    }

    @GetMapping("/payments/pending")
    @Operation(summary = "Listar los pagos PENDIENTE ya vencidos del usuario autenticado",
               description = "Alimenta el badge de \"pagos por cobrar\" en la vista de Inversiones.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Pagos pendientes vencidos",
            content = @Content(mediaType = "application/json",
                array = @ArraySchema(schema = @Schema(implementation = PendingPaymentResponse.class)))),
        @ApiResponse(responseCode = "401", description = "Token JWT ausente o inválido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Error interno del servidor",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public List<PendingPaymentResponse> getPendingPayments(@AuthenticationPrincipal User user) {
        return investmentPaymentService.getPendingPayments(user);
    }
}
