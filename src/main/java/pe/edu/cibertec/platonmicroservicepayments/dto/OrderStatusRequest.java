package pe.edu.cibertec.platonmicroservicepayments.dto;

import jakarta.validation.constraints.NotNull;
import pe.edu.cibertec.platonmicroservicepayments.model.OrderStatus;

public record OrderStatusRequest(@NotNull OrderStatus orderStatus) {}
