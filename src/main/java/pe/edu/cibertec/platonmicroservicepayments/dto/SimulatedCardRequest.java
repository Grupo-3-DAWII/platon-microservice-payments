package pe.edu.cibertec.platonmicroservicepayments.dto;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public record SimulatedCardRequest(@NotNull @DecimalMin("0.01") BigDecimal amount,
                                   @NotBlank String testToken) {}
