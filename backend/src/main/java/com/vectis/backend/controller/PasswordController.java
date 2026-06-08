package com.vectis.backend.controller;

import com.vectis.backend.domain.entity.User;
import com.vectis.backend.dto.ChangePasswordRequest;
import com.vectis.backend.dto.ForgotPasswordRequest;
import com.vectis.backend.dto.ResetPasswordRequest;
import com.vectis.backend.exception.GlobalExceptionHandler.ErrorResponse;
import com.vectis.backend.service.PasswordService;
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

@RestController
@RequiredArgsConstructor
@Tag(name = "Password", description = "Recuperación y cambio de contraseña")
public class PasswordController {

    private final PasswordService passwordService;

    @PostMapping("/api/auth/forgot-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Solicitar email de recuperación de contraseña")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Email enviado (si el correo existe en el sistema)"),
        @ApiResponse(responseCode = "400", description = "Formato de email inválido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public void forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordService.forgotPassword(request.email());
    }

    @PostMapping("/api/auth/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Restablecer contraseña usando el token del email")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Contraseña restablecida exitosamente"),
        @ApiResponse(responseCode = "400", description = "Datos inválidos",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Token inválido, expirado o ya utilizado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordService.resetPassword(request.token(), request.newPassword());
    }

    @PatchMapping("/api/users/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "Cambiar contraseña del usuario autenticado")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Contraseña actualizada exitosamente"),
        @ApiResponse(responseCode = "400", description = "Datos inválidos",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "No autenticado o contraseña actual incorrecta",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public void changePassword(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody ChangePasswordRequest request) {
        passwordService.changePassword(currentUser.getId(), request.currentPassword(), request.newPassword());
    }
}
