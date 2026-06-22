package com.vectis.backend.controller;

import com.vectis.backend.domain.entity.User;
import com.vectis.backend.dto.TransferRequest;
import com.vectis.backend.dto.TransferResponse;
import com.vectis.backend.exception.GlobalExceptionHandler.ErrorResponse;
import com.vectis.backend.service.TransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/transfers")
@RequiredArgsConstructor
@Tag(name = "Transferencias", description = "Transferencias entre cuentas propias del usuario")
@SecurityRequirement(name = "BearerAuth")
public class TransferController {

    private final TransferService transferService;

    @PostMapping
    @Operation(summary = "Registra una transferencia entre cuentas propias",
               description = "Crea dos transacciones vinculadas: un egreso en la cuenta origen " +
                             "y un ingreso en la cuenta destino. Soporta conversión de moneda si las cuentas difieren en ccy.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Transferencia registrada",
            content = @Content(schema = @Schema(implementation = TransferResponse.class))),
        @ApiResponse(responseCode = "400", description = "Datos inválidos (misma cuenta, monto negativo, etc.)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Token JWT ausente o inválido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Cuenta origen o destino no encontrada",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<TransferResponse> create(
            @Valid @RequestBody TransferRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(transferService.create(request, user));
    }

    @DeleteMapping("/{transferGroupId}")
    @Operation(summary = "Elimina una transferencia (ambas legs)",
               description = "Soft-delete atómico de las dos transacciones vinculadas por el transferGroupId.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Transferencia eliminada"),
        @ApiResponse(responseCode = "401", description = "Token JWT ausente o inválido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Transferencia no encontrada",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> delete(
            @PathVariable UUID transferGroupId,
            @AuthenticationPrincipal User user) {
        transferService.delete(transferGroupId, user);
        return ResponseEntity.noContent().build();
    }
}
