package pe.edu.cibertec.platonmicroservicepayments.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record PaymentRequest(
        @NotNull @DecimalMin("0.00") @Digits(integer = 16, fraction = 2) BigDecimal amount
) {}
