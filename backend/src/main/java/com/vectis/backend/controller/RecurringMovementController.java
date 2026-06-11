package com.vectis.backend.controller;

import com.vectis.backend.domain.entity.User;
import com.vectis.backend.dto.RecurringMovementRequest;
import com.vectis.backend.dto.RecurringMovementResponse;
import com.vectis.backend.exception.GlobalExceptionHandler.ErrorResponse;
import com.vectis.backend.service.RecurringMovementService;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/recurring-movements")
@RequiredArgsConstructor
@Tag(name = "Recurring Movements", description = "Gestión de movimientos recurrentes del usuario")
@SecurityRequirement(name = "BearerAuth")
public class RecurringMovementController {

    private final RecurringMovementService recurringMovementService;

    @GetMapping
    @Operation(summary = "Listar movimientos recurrentes del usuario autenticado")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de movimientos recurrentes"),
        @ApiResponse(responseCode = "401", description = "Token JWT ausente o inválido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public List<RecurringMovementResponse> getRecurringMovements(@AuthenticationPrincipal User user) {
        return recurringMovementService.getRecurringMovements(user.getId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crear un movimiento recurrente")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Movimiento recurrente creado"),
        @ApiResponse(responseCode = "400", description = "Datos de entrada inválidos",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Token JWT ausente o inválido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "La cuenta no pertenece al usuario autenticado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Categoría o cuenta no encontrada",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public RecurringMovementResponse createRecurringMovement(
            @Valid @RequestBody RecurringMovementRequest request,
            @AuthenticationPrincipal User user) {
        return recurringMovementService.createRecurringMovement(request, user);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Editar un movimiento recurrente propio")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Movimiento recurrente actualizado"),
        @ApiResponse(responseCode = "400", description = "Datos de entrada inválidos",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Token JWT ausente o inválido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "No se puede editar un movimiento de otro usuario",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Movimiento recurrente no encontrado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public RecurringMovementResponse updateRecurringMovement(
            @PathVariable UUID id,
            @Valid @RequestBody RecurringMovementRequest request,
            @AuthenticationPrincipal User user) {
        return recurringMovementService.updateRecurringMovement(id, request, user);
    }

    @PatchMapping("/{id}/toggle")
    @Operation(summary = "Activar o desactivar un movimiento recurrente")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Estado del movimiento recurrente actualizado"),
        @ApiResponse(responseCode = "401", description = "Token JWT ausente o inválido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "No se puede modificar un movimiento de otro usuario",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Movimiento recurrente no encontrado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public RecurringMovementResponse toggleActive(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user) {
        return recurringMovementService.toggleActive(id, user);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Eliminar (soft delete) un movimiento recurrente propio")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Movimiento recurrente eliminado"),
        @ApiResponse(responseCode = "401", description = "Token JWT ausente o inválido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "No se puede eliminar un movimiento de otro usuario",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Movimiento recurrente no encontrado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public void deleteRecurringMovement(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user) {
        recurringMovementService.deleteRecurringMovement(id, user);
    }
}
