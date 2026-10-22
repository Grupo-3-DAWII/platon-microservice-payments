package pe.edu.cibertec.platonmicroservicepayments.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record PurchaseRequest(
        @NotNull @Positive Long idUser,
        @NotNull @Positive Long idStateSold,
        @NotEmpty List<@NotNull @Valid Item> products
) {
    public record Item(
            @NotNull @Positive Long idProducts,
            @NotNull @Min(1) Integer numberSold
    ) {}
}
