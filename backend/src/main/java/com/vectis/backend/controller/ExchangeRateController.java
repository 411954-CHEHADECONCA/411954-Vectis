package com.vectis.backend.controller;

import com.vectis.backend.dto.ExchangeRateResponse;
import com.vectis.backend.dto.InflationResponse;
import com.vectis.backend.exception.GlobalExceptionHandler.ErrorResponse;
import com.vectis.backend.service.MacroDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Macro Data", description = "Cotizaciones e indices macroeconomicos")
public class ExchangeRateController {

    private final MacroDataService macroDataService;

    @GetMapping("/exchange-rates/oficial/latest")
    @Operation(summary = "Obtiene la cotizacion del dolar oficial mas reciente (dolarapi.com)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Cotizacion encontrada",
            content = @Content(schema = @Schema(implementation = ExchangeRateResponse.class))),
        @ApiResponse(responseCode = "401", description = "No autenticado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Sin datos de cotizacion disponibles",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @SecurityRequirement(name = "BearerAuth")
    public ResponseEntity<ExchangeRateResponse> getLatestOficialRate() {
        return ResponseEntity.ok(macroDataService.getLatestOficialRate());
    }

    @GetMapping("/exchange-rates/mep/latest")
    @Operation(summary = "Obtiene la cotizacion MEP mas reciente (argentinadatos.com)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Cotizacion encontrada",
            content = @Content(schema = @Schema(implementation = ExchangeRateResponse.class))),
        @ApiResponse(responseCode = "401", description = "No autenticado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Sin datos de cotizacion disponibles",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @SecurityRequirement(name = "BearerAuth")
    public ResponseEntity<ExchangeRateResponse> getLatestMepRate() {
        return ResponseEntity.ok(macroDataService.getLatestMepRate());
    }

    @GetMapping("/inflation/latest")
    @Operation(summary = "Obtiene el ultimo registro de inflacion mensual (IPC)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Registro de IPC encontrado",
            content = @Content(schema = @Schema(implementation = InflationResponse.class))),
        @ApiResponse(responseCode = "401", description = "No autenticado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Sin datos de inflacion disponibles",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @SecurityRequirement(name = "BearerAuth")
    public ResponseEntity<InflationResponse> getLatestInflation() {
        return ResponseEntity.ok(macroDataService.getLatestInflation());
    }
}
