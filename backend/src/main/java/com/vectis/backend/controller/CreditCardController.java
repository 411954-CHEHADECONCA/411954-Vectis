package com.vectis.backend.controller;

import com.vectis.backend.domain.entity.User;
import com.vectis.backend.dto.CardRequest;
import com.vectis.backend.dto.CardResponse;
import com.vectis.backend.exception.GlobalExceptionHandler.ErrorResponse;
import com.vectis.backend.service.CreditCardService;
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
@RequestMapping("/api/cards")
@RequiredArgsConstructor
@Tag(name = "Credit Cards", description = "Gestión de tarjetas de crédito del usuario")
@SecurityRequirement(name = "BearerAuth")
public class CreditCardController {

    private final CreditCardService creditCardService;

    @GetMapping
    @Operation(summary = "Listar tarjetas del usuario autenticado")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de tarjetas"),
        @ApiResponse(responseCode = "401", description = "Token JWT ausente o inválido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public List<CardResponse> getCards(@AuthenticationPrincipal User user) {
        return creditCardService.getCards(user.getId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crear una tarjeta de crédito")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Tarjeta creada"),
        @ApiResponse(responseCode = "400", description = "Datos de entrada inválidos",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Token JWT ausente o inválido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public CardResponse createCard(
            @Valid @RequestBody CardRequest request,
            @AuthenticationPrincipal User user) {
        return creditCardService.createCard(request, user);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Editar una tarjeta propia")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Tarjeta actualizada"),
        @ApiResponse(responseCode = "400", description = "Datos de entrada inválidos",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Token JWT ausente o inválido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "No se puede editar una tarjeta de otro usuario",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Tarjeta no encontrada",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public CardResponse updateCard(
            @PathVariable UUID id,
            @Valid @RequestBody CardRequest request,
            @AuthenticationPrincipal User user) {
        return creditCardService.updateCard(id, request, user);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Eliminar una tarjeta propia")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Tarjeta eliminada"),
        @ApiResponse(responseCode = "401", description = "Token JWT ausente o inválido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "No se puede eliminar una tarjeta de otro usuario",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Tarjeta no encontrada",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public void deleteCard(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user) {
        creditCardService.deleteCard(id, user);
    }
}
