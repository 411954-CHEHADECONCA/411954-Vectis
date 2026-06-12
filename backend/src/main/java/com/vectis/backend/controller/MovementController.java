package com.vectis.backend.controller;

import com.vectis.backend.domain.entity.User;
import com.vectis.backend.dto.MovementRequest;
import com.vectis.backend.dto.MovementResponse;
import com.vectis.backend.dto.MovementSummaryResponse;
import com.vectis.backend.dto.PageResponse;
import com.vectis.backend.exception.GlobalExceptionHandler.ErrorResponse;
import com.vectis.backend.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/movements")
@RequiredArgsConstructor
@Tag(name = "Movements", description = "Libro de movimientos (transacciones) del usuario")
@SecurityRequirement(name = "BearerAuth")
public class MovementController {

    private final TransactionService transactionService;

    @GetMapping
    @Operation(summary = "Listar movimientos del usuario (paginado, con filtros por período/tipo/categoría/búsqueda)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Página de movimientos"),
        @ApiResponse(responseCode = "401", description = "Token JWT ausente o inválido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public PageResponse<MovementResponse> getMovements(
            @Parameter(description = "Desde (inclusive). Default: día 1 del mes actual")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Hasta (inclusive). Default: último día del mes actual")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "Filtro por tipo", schema = @Schema(allowableValues = {"INCOME", "EXPENSE"}))
            @RequestParam(required = false) String type,
            @RequestParam(required = false) UUID categoryId,
            @Parameter(description = "Búsqueda por descripción")
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal User user) {
        LocalDate[] range = resolveRange(from, to);
        return transactionService.search(user.getId(), range[0], range[1], type, categoryId, q, page, size);
    }

    @GetMapping("/summary")
    @Operation(summary = "Resumen agregado (ingresos/egresos/neto/cantidad) del período/filtros")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Resumen del período"),
        @ApiResponse(responseCode = "401", description = "Token JWT ausente o inválido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public MovementSummaryResponse getSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String q,
            @AuthenticationPrincipal User user) {
        LocalDate[] range = resolveRange(from, to);
        return transactionService.summary(user.getId(), range[0], range[1], type, categoryId, q);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registrar un movimiento (genera N filas si es en cuotas)")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Movimiento(s) creado(s)"),
        @ApiResponse(responseCode = "400", description = "Datos inválidos (p. ej. cuotas sin tarjeta, cuenta y tarjeta juntas)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Token JWT ausente o inválido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Cuenta o tarjeta de otro usuario",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Categoría, cuenta o tarjeta no encontrada",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public List<MovementResponse> createMovement(
            @Valid @RequestBody MovementRequest request,
            @AuthenticationPrincipal User user) {
        return transactionService.create(request, user);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Editar un movimiento propio (no aplica a cuotas)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Movimiento actualizado"),
        @ApiResponse(responseCode = "400", description = "Datos inválidos o intento de editar una cuota",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Token JWT ausente o inválido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Movimiento de otro usuario",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Movimiento no encontrado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public MovementResponse updateMovement(
            @PathVariable UUID id,
            @Valid @RequestBody MovementRequest request,
            @AuthenticationPrincipal User user) {
        return transactionService.update(id, request, user);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Eliminar (soft delete) un movimiento propio; si es cuota, elimina todo el grupo")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Movimiento eliminado"),
        @ApiResponse(responseCode = "401", description = "Token JWT ausente o inválido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Movimiento de otro usuario",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Movimiento no encontrado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public void deleteMovement(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user) {
        transactionService.delete(id, user);
    }

    /** Default del período: mes actual completo (día 1 → fin de mes). */
    private LocalDate[] resolveRange(LocalDate from, LocalDate to) {
        YearMonth now = YearMonth.now();
        LocalDate start = from != null ? from : now.atDay(1);
        LocalDate end   = to   != null ? to   : now.atEndOfMonth();
        return new LocalDate[]{start, end};
    }
}
