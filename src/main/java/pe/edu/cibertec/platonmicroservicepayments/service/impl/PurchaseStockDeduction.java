package pe.edu.cibertec.platonmicroservicepayments.service.impl;

import pe.edu.cibertec.platonmicroservicepayments.dto.ProductsSoldRequest;
import java.util.UUID;
import java.math.BigDecimal;

public record PurchaseStockDeduction(ProductsSoldRequest request, UUID deductionId, BigDecimal unitPrice) {}
