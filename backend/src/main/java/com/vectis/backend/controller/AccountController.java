package com.vectis.backend.controller;

import com.vectis.backend.domain.entity.User;
import com.vectis.backend.dto.AccountRequest;
import com.vectis.backend.dto.AccountResponse;
import com.vectis.backend.exception.GlobalExceptionHandler.ErrorResponse;
import com.vectis.backend.service.AccountService;
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
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
@Tag(name = "Accounts", description = "Gestión de cuentas líquidas del usuario")
@SecurityRequirement(name = "BearerAuth")
public class AccountController {

    private final AccountService accountService;

    @GetMapping
    @Operation(summary = "Listar cuentas del usuario autenticado")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de cuentas"),
        @ApiResponse(responseCode = "401", description = "Token JWT ausente o inválido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public List<AccountResponse> getAccounts(@AuthenticationPrincipal User user) {
        return accountService.getAccounts(user.getId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crear una cuenta líquida")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Cuenta creada"),
        @ApiResponse(responseCode = "400", description = "Datos de entrada inválidos",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Token JWT ausente o inválido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public AccountResponse createAccount(
            @Valid @RequestBody AccountRequest request,
            @AuthenticationPrincipal User user) {
        return accountService.createAccount(request, user);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Editar una cuenta propia")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Cuenta actualizada"),
        @ApiResponse(responseCode = "400", description = "Datos de entrada inválidos",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Token JWT ausente o inválido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "No se puede editar una cuenta de otro usuario",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Cuenta no encontrada",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public AccountResponse updateAccount(
            @PathVariable UUID id,
            @Valid @RequestBody AccountRequest request,
            @AuthenticationPrincipal User user) {
        return accountService.updateAccount(id, request, user);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Eliminar una cuenta propia")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Cuenta eliminada"),
        @ApiResponse(responseCode = "401", description = "Token JWT ausente o inválido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "No se puede eliminar una cuenta de otro usuario",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Cuenta no encontrada",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public void deleteAccount(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user) {
        accountService.deleteAccount(id, user);
    }
}
