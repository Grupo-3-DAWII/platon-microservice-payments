package pe.edu.cibertec.platonmicroservicepayments.dto;

import pe.edu.cibertec.platonmicroservicepayments.model.OrderStatus;

public record OrderStatusResponse(Long idProductsSold, OrderStatus orderStatus) {}
