package com.vectis.backend.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
    info = @Info(
        title = "Vectis Finance API",
        version = "1.0.0",
        description = "API para la aplicación de finanzas bimonetaria Vectis (ARS/USD). " +
                      "Todos los endpoints protegidos requieren un Bearer JWT en el header Authorization.",
        contact = @Contact(name = "Santiago Chehade", email = "chehadesantiago@gmail.com")
    ),
    servers = {
        @Server(url = "/", description = "Local / Docker")
    }
)
@SecurityScheme(
    name = "BearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT",
    in = SecuritySchemeIn.HEADER,
    description = "Token JWT obtenido en /api/auth/login. Formato: Bearer <token>"
)
public class OpenApiConfig {}
