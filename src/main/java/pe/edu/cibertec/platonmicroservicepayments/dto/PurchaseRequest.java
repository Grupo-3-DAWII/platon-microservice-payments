package pe.edu.cibertec.platonmicroservicepayments.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.math.BigDecimal;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;

public record PurchaseRequest(
        @NotNull @Positive Long idUser,
        @NotNull @Positive Long idStateSold,
        @NotEmpty List<@NotNull @Valid Item> products,
        @NotNull @DecimalMin("0.00") @Digits(integer = 16, fraction = 2) BigDecimal totalAmount
) {
    public record Item(
            @NotNull @Positive Long idProducts,
            @NotNull @Min(1) Integer numberSold
    ) {}
}
