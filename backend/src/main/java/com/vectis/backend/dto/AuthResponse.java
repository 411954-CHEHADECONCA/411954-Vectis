package com.vectis.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Respuesta de autenticación con tokens JWT e información del usuario")
public class AuthResponse {

    @Schema(description = "Access token JWT (válido 15 minutos)", example = "eyJhbGc...")
    private String accessToken;

    @Schema(description = "Refresh token (válido 7 días)", example = "eyJhbGc...")
    private String refreshToken;

    @Builder.Default
    @Schema(description = "Tipo de token", example = "Bearer")
    private String tokenType = "Bearer";

    @Schema(description = "Información básica del usuario autenticado")
    private UserInfo user;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Datos del usuario autenticado")
    public static class UserInfo {
        @Schema(description = "UUID del usuario", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private UUID id;

        @Schema(description = "Email del usuario", example = "usuario@ejemplo.com")
        private String email;

        @Schema(description = "Nombre completo", example = "Juan Pérez")
        private String fullName;
    }
}
