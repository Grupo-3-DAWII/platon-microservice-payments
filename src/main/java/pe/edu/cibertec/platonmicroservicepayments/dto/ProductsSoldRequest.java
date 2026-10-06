package pe.edu.cibertec.platonmicroservicepayments.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ProductsSoldRequest(
        @NotNull(message = "El id del producto es obligatorio")
        Long idProducts,

        @NotNull(message = "El id del usuario es obligatorio")
        Long idUser,

        @NotNull(message = "La cantidad vendida es obligatoria")
        @Min(value = 1, message = "La cantidad vendida debe ser mayor a 0")
        Integer numberSold,

        @NotNull(message = "El id del estado es obligatorio")
        Long idStateSold
) {}
