package com.vectis.backend.controller;

import com.vectis.backend.config.PpiMarketDataClient;
import com.vectis.backend.domain.entity.User;
import com.vectis.backend.dto.FciFundDto;
import com.vectis.backend.dto.InstrumentDto;
import com.vectis.backend.dto.InstrumentPriceResponse;
import com.vectis.backend.dto.MarketApiStatusDto;
import com.vectis.backend.dto.InvestmentMovementRequest;
import com.vectis.backend.dto.InvestmentMovementUpdateRequest;
import com.vectis.backend.dto.InvestmentRequest;
import com.vectis.backend.dto.InvestmentResponse;
import com.vectis.backend.dto.InvestmentValuationRequest;
import com.vectis.backend.exception.GlobalExceptionHandler.ErrorResponse;
import com.vectis.backend.exception.VectisException;
import com.vectis.backend.service.FciValuationSyncService;
import com.vectis.backend.service.PpiValuationSyncService;
import com.vectis.backend.service.InvestmentService;
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
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/investments")
@RequiredArgsConstructor
@Tag(name = "Investments", description = "Gestión de activos de inversión del usuario")
@SecurityRequirement(name = "BearerAuth")
public class InvestmentController {

    private final InvestmentService        investmentService;
    private final FciValuationSyncService  fciValuationSyncService;
    private final PpiValuationSyncService  ppiValuationSyncService;
    private final PpiMarketDataClient      ppiMarketDataClient;

    @GetMapping
    @Operation(summary = "Listar activos de inversión del usuario autenticado")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de activos",
            content = @Content(mediaType = "application/json",
                array = @ArraySchema(schema = @Schema(implementation = InvestmentResponse.class)))),
        @ApiResponse(responseCode = "401", description = "Token JWT ausente o inválido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Error interno del servidor",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public List<InvestmentResponse> getInvestments(@AuthenticationPrincipal User user) {
        return investmentService.getInvestments(user.getId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crear un activo de inversión")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Activo creado",
            content = @Content(schema = @Schema(implementation = InvestmentResponse.class))),
        @ApiResponse(responseCode = "400", description = "Datos de entrada inválidos",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Token JWT ausente o inválido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "La cuenta indicada no pertenece al usuario autenticado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Error interno del servidor",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public InvestmentResponse createInvestment(
            @Valid @RequestBody InvestmentRequest request,
            @AuthenticationPrincipal User user) {
        return investmentService.createInvestment(request, user);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Editar un activo de inversión propio")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Activo actualizado",
            content = @Content(schema = @Schema(implementation = InvestmentResponse.class))),
        @ApiResponse(responseCode = "400", description = "Datos de entrada inválidos",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Token JWT ausente o inválido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "No se puede editar un activo de otro usuario, o la cuenta indicada no pertenece al usuario autenticado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Activo no encontrado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Error interno del servidor",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public InvestmentResponse updateInvestment(
            @PathVariable UUID id,
            @Valid @RequestBody InvestmentRequest request,
            @AuthenticationPrincipal User user) {
        return investmentService.updateInvestment(id, request, user);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Eliminar un activo de inversión propio")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Activo eliminado"),
        @ApiResponse(responseCode = "401", description = "Token JWT ausente o inválido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "No se puede eliminar un activo de otro usuario",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Activo no encontrado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Error interno del servidor",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public void deleteInvestment(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user) {
        investmentService.deleteInvestment(id, user);
    }

    // ── Movements (FCI) ──────────────────────────────────────────────────────

    @PostMapping("/{id}/movements")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Agregar un movimiento (suscripción o rescate) a un FCI")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Movimiento registrado — retorna el activo actualizado",
            content = @Content(schema = @Schema(implementation = InvestmentResponse.class))),
        @ApiResponse(responseCode = "400", description = "Datos de entrada inválidos",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Token JWT ausente o inválido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Activo no encontrado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "422", description = "Rescate supera el saldo disponible",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Error interno del servidor",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public InvestmentResponse addMovement(
            @PathVariable UUID id,
            @Valid @RequestBody InvestmentMovementRequest request,
            @AuthenticationPrincipal User user) {
        return investmentService.addMovement(id, request, user);
    }

    @PutMapping("/{id}/movements/{movId}")
    @Operation(summary = "Editar un movimiento de una Cuenta Remunerada (FCI)",
               description = "Ajusta el monto del movimiento (tramo REVALUO, capitaliza) y/o fija el override "
                       + "de interés del tramo (SUSCRIPCION/RESCATE, no capitaliza). Solo aplica a activos FCI.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Movimiento actualizado — retorna el activo actualizado",
            content = @Content(schema = @Schema(implementation = InvestmentResponse.class))),
        @ApiResponse(responseCode = "400", description = "Datos de entrada inválidos",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Token JWT ausente o inválido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Activo o movimiento no encontrado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "El activo no es una Cuenta Remunerada (FCI)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Error interno del servidor",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public InvestmentResponse updateMovement(
            @PathVariable UUID id,
            @PathVariable UUID movId,
            @Valid @RequestBody InvestmentMovementUpdateRequest request,
            @AuthenticationPrincipal User user) {
        return investmentService.updateMovement(id, movId, request, user);
    }

    @DeleteMapping("/{id}/movements/{movId}")
    @Operation(summary = "Eliminar un movimiento de un FCI")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Movimiento eliminado — retorna el activo actualizado",
            content = @Content(schema = @Schema(implementation = InvestmentResponse.class))),
        @ApiResponse(responseCode = "401", description = "Token JWT ausente o inválido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Activo o movimiento no encontrado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Error interno del servidor",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public InvestmentResponse deleteMovement(
            @PathVariable UUID id,
            @PathVariable UUID movId,
            @AuthenticationPrincipal User user) {
        return investmentService.deleteMovement(id, movId, user);
    }

    // ── Valuations (FCI_CUOTAPARTES) ─────────────────────────────────────────

    @PostMapping("/{id}/valuations")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Agregar una valuación (corte de precio) a un FCI Cuotapartes")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Valuación registrada — retorna el activo actualizado",
            content = @Content(schema = @Schema(implementation = InvestmentResponse.class))),
        @ApiResponse(responseCode = "400", description = "Datos de entrada inválidos",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Token JWT ausente o inválido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Activo no encontrado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Error interno del servidor",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public InvestmentResponse addValuation(
            @PathVariable UUID id,
            @Valid @RequestBody InvestmentValuationRequest request,
            @AuthenticationPrincipal User user) {
        return investmentService.addValuation(id, request, user);
    }

    @PutMapping("/{id}/valuations/{valId}")
    @Operation(summary = "Actualizar una valuación existente de un FCI Cuotapartes")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Valuación actualizada — retorna el activo actualizado",
            content = @Content(schema = @Schema(implementation = InvestmentResponse.class))),
        @ApiResponse(responseCode = "400", description = "Datos de entrada inválidos",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Token JWT ausente o inválido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Activo o valuación no encontrado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Error interno del servidor",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public InvestmentResponse updateValuation(
            @PathVariable UUID id,
            @PathVariable UUID valId,
            @Valid @RequestBody InvestmentValuationRequest request,
            @AuthenticationPrincipal User user) {
        return investmentService.updateValuation(id, valId, request, user);
    }

    @DeleteMapping("/{id}/valuations/{valId}")
    @Operation(summary = "Eliminar una valuación de un FCI Cuotapartes")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Valuación eliminada — retorna el activo actualizado",
            content = @Content(schema = @Schema(implementation = InvestmentResponse.class))),
        @ApiResponse(responseCode = "401", description = "Token JWT ausente o inválido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Activo o valuación no encontrado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Error interno del servidor",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public InvestmentResponse deleteValuation(
            @PathVariable UUID id,
            @PathVariable UUID valId,
            @AuthenticationPrincipal User user) {
        return investmentService.deleteValuation(id, valId, user);
    }

    // ── Market catalog ───────────────────────────────────────────────────────

    @GetMapping("/market/fci-funds")
    @Operation(summary = "Listar fondos FCI disponibles para seguimiento automático",
               description = "Devuelve el catálogo de fondos FCI del último snapshot disponible, filtrado por categoría.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de fondos FCI",
            content = @Content(mediaType = "application/json",
                array = @ArraySchema(schema = @Schema(implementation = FciFundDto.class)))),
        @ApiResponse(responseCode = "401", description = "Token JWT ausente o inválido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Error interno del servidor",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public List<FciFundDto> getFciFunds(
            @RequestParam(defaultValue = "mercadoDinero") String categoria) {
        return fciValuationSyncService.getLatestFciFunds(categoria);
    }

    @GetMapping("/market/status")
    @Operation(summary = "Estado de sincronización de las APIs de mercado",
               description = "Devuelve conteo de snapshots FCI, fecha de último sync y estado de PPI.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Estado de las APIs",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = MarketApiStatusDto.class))),
        @ApiResponse(responseCode = "401", description = "Token JWT ausente o inválido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public MarketApiStatusDto getMarketApiStatus() {
        return new MarketApiStatusDto(
            fciValuationSyncService.getSnapshotCount(),
            fciValuationSyncService.getLastSyncDate(),
            ppiMarketDataClient.isConfigured()
        );
    }

    @GetMapping("/market/instruments")
    @Operation(summary = "Listar instrumentos de renta fija disponibles para seguimiento automático",
               description = "Devuelve el catálogo cacheado de instrumentos LETRA/BONO/ON.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de instrumentos",
            content = @Content(mediaType = "application/json",
                array = @ArraySchema(schema = @Schema(implementation = InstrumentDto.class)))),
        @ApiResponse(responseCode = "401", description = "Token JWT ausente o inválido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Error interno del servidor",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public List<InstrumentDto> getInstruments(
            @RequestParam(required = false) String tipo) {
        return ppiValuationSyncService.getInstrumentsByType(tipo);
    }

    @GetMapping("/market/fci-vcp")
    @Operation(summary = "Obtiene el VCP de un fondo FCI para una fecha específica",
               description = "Busca en los snapshots persistidos el Valor de Cuotaparte del fondo indicado para la fecha solicitada. "
                       + "Si no hay dato disponible devuelve 204 No Content (no es un error): la ausencia de precio es un "
                       + "resultado válido para instrumentos/fechas sin cotización.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "VCP encontrado para la fecha indicada",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = FciFundDto.class))),
        @ApiResponse(responseCode = "204", description = "No hay VCP disponible para el fondo y fecha (sin cuerpo)"),
        @ApiResponse(responseCode = "401", description = "Token JWT ausente o inválido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Error interno del servidor",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<FciFundDto> getFciVcp(
            @RequestParam String fondo,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        // "Sin precio" es un resultado válido, no un error: se devuelve 204 (2xx, no lo marca en rojo el
        // navegador) en lugar de 404. HttpClient de Angular emite null ante 204, igual que antes.
        return fciValuationSyncService.getVcpForDate(fondo, fecha)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/market/instrument-price")
    @Operation(summary = "Obtiene el precio de un instrumento (BONO/LETRA/ON) para una fecha",
               description = "Consulta a PPI (Portfolio Personal Inversiones) el precio del instrumento indicado para la fecha solicitada. "
                       + "Requiere credenciales PPI configuradas. Si no hay precio disponible devuelve 204 No Content (no es un "
                       + "error): la ausencia de cotización es un resultado válido para ciertos instrumentos/fechas.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Precio encontrado para la fecha indicada",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = InstrumentPriceResponse.class))),
        @ApiResponse(responseCode = "204", description = "No hay cotización disponible para el instrumento y fecha (sin cuerpo)"),
        @ApiResponse(responseCode = "400", description = "Tipo de instrumento inválido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Token JWT ausente o inválido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "503", description = "Integración PPI no configurada",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<InstrumentPriceResponse> getInstrumentPrice(
            @RequestParam String ticker,
            @RequestParam String type,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        if (!ppiMarketDataClient.isConfigured()) {
            throw new VectisException("PPI no configurado", HttpStatus.SERVICE_UNAVAILABLE);
        }
        String ppiType = mapToPpiType(type);
        // "Sin precio" es un resultado válido, no un error: se devuelve 204 (2xx, no lo marca en rojo el
        // navegador) en lugar de 404. HttpClient de Angular emite null ante 204, igual que antes.
        // La `fecha` de la respuesta es la del CIERRE REAL devuelto por PPI (puede ser anterior a la
        // solicitada para instrumentos ilíquidos), para que el front persista la valuación en esa fecha.
        return ppiMarketDataClient.getPriceForDate(ticker, ppiType, fecha)
                .map(dp -> ResponseEntity.ok(
                        new InstrumentPriceResponse(ticker, type, dp.date().toString(), dp.price())))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private static final Set<String> VALID_TYPES = Set.of("BONO", "LETRA", "ON");

    /**
     * Maps the Vectis instrument type to PPI's internal type string.
     * Only accepts the whitelist {BONO, LETRA, ON}; throws 400 for anything else.
     */
    private String mapToPpiType(String type) {
        String upper = type.toUpperCase();
        if (!VALID_TYPES.contains(upper)) {
            throw new VectisException(
                    "Tipo de instrumento inválido: " + type + ". Valores permitidos: " + VALID_TYPES,
                    HttpStatus.BAD_REQUEST);
        }
        return switch (upper) {
            case "BONO"  -> "BONOS";
            case "LETRA" -> "LETRAS";
            default      -> upper; // ON → ON
        };
    }
}
