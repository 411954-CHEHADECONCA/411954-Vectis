package com.vectis.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Restablecimiento de contraseña con token de recuperación")
public record ResetPasswordRequest(
    @NotBlank(message = "Token is required")
    @Schema(description = "Token recibido por email", example = "a1b2c3d4e5f6...")
    String token,

    @NotBlank(message = "New password is required")
    @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
    @Schema(description = "Nueva contraseña (mínimo 8 caracteres)", example = "nuevaContraseña123")
    String newPassword
) {}
