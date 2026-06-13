package com.vectis.backend.controller;

import com.vectis.backend.domain.entity.User;
import com.vectis.backend.dto.CardMatrixResponse;
import com.vectis.backend.dto.CardOverviewResponse;
import com.vectis.backend.exception.GlobalExceptionHandler.ErrorResponse;
import com.vectis.backend.service.CardProjectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cards")
@RequiredArgsConstructor
@Validated
@Tag(name = "Card Projection", description = "Proyección de deuda de tarjetas: resumen y matriz")
@SecurityRequirement(name = "BearerAuth")
public class CardProjectionController {

    private final CardProjectionService cardProjectionService;

    @GetMapping("/overview")
    @Operation(summary = "Resumen de tarjetas: caras, total a pagar, próximo vencimiento, cuotas activas y vencimientos")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Resumen de tarjetas"),
        @ApiResponse(responseCode = "401", description = "Token JWT ausente o inválido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public CardOverviewResponse getOverview(@AuthenticationPrincipal User user) {
        return cardProjectionService.overview(user.getId());
    }

    @GetMapping("/matrix")
    @Operation(summary = "Matriz de proyección de deuda diferida por tarjeta y mes")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Matriz de deuda diferida"),
        @ApiResponse(responseCode = "401", description = "Token JWT ausente o inválido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public CardMatrixResponse getMatrix(
            @Parameter(description = "Cantidad de meses a proyectar (columnas)")
            @RequestParam(defaultValue = "6") @Min(1) @Max(24) int months,
            @AuthenticationPrincipal User user) {
        return cardProjectionService.matrix(user.getId(), months);
    }
}
