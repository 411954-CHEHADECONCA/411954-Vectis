package com.vectis.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@Schema(description = "Actualización de un plan de cuotas: solo descripción y categoría")
public record GroupMovementUpdateRequest(

        @Schema(description = "Descripción base del plan (sin el sufijo de cuota)", example = "Notebook")
        @NotBlank(message = "La descripción es obligatoria")
        @Size(max = 200, message = "La descripción no puede superar los 200 caracteres")
        String description,

        @Schema(description = "ID de la categoría asociada, o null para asignar la categoría default (\"Otros egresos\")")
        UUID categoryId
) {}
