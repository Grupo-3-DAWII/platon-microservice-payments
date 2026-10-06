package pe.edu.cibertec.platonmicroservicepayments.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StateSoldRequest(
        @NotBlank(message = "El nombre del estado es obligatorio")
        @Size(max = 100, message = "El nombre del estado no puede superar los 100 caracteres")
        String nameState
) {}
