package pe.edu.cibertec.platonmicroservicepayments.service.impl;

import pe.edu.cibertec.platonmicroservicepayments.dto.ProductsSoldRequest;
import java.util.UUID;

public record PurchaseStockDeduction(ProductsSoldRequest request, UUID deductionId) {}
