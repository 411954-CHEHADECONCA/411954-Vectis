package com.vectis.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Cambio de contraseña para usuario autenticado")
public record ChangePasswordRequest(
    @NotBlank(message = "Current password is required")
    @Schema(description = "Contraseña actual del usuario", example = "contraseñaActual123")
    String currentPassword,

    @NotBlank(message = "New password is required")
    @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
    @Schema(description = "Nueva contraseña (mínimo 8 caracteres)", example = "nuevaContraseña456")
    String newPassword
) {}
