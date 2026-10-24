package pe.edu.cibertec.platonmicroservicepayments.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.cibertec.platonmicroservicepayments.dto.PurchaseResponse;
import pe.edu.cibertec.platonmicroservicepayments.exception.ProductUnavailableException;
import pe.edu.cibertec.platonmicroservicepayments.exception.ResourceNotFoundException;
import pe.edu.cibertec.platonmicroservicepayments.mapper.ProductsSoldMapper;
import pe.edu.cibertec.platonmicroservicepayments.repository.ProductsSoldRepository;
import pe.edu.cibertec.platonmicroservicepayments.repository.PurchaseOutboxRepository;
import pe.edu.cibertec.platonmicroservicepayments.repository.StateSoldRepository;
import pe.edu.cibertec.platonmicroservicepayments.repository.UserProductsRepository;
import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class PaymentDecisionService {
    private final ProductsSoldRepository purchases;
    private final StateSoldRepository states;
    private final ProductsSoldMapper mapper;
    private final UserProductsRepository userProducts;
    private final PurchaseOutboxRepository outbox;

    @Transactional
    public PurchaseResponse accept(Long id, BigDecimal amount) {
        return decide(id, true, amount);
    }

    @Transactional
    public PurchaseResponse reject(Long id) {
        return decide(id, false, null);
    }

    @Transactional
    public PurchaseResponse simulateCard(Long id, Long userId, BigDecimal amount, String testToken) {
        if (!"visa-platinum-test".equals(testToken)) {
            throw new ProductUnavailableException("Solo se admite la tarjeta de prueba Visa Platinum");
        }
        var purchase = purchases.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido no encontrado"));
        if (!purchase.getIdUser().equals(userId)) {
            throw new ResourceNotFoundException("Pedido no encontrado");
        }
        if ("PAGADO".equalsIgnoreCase(purchase.getStateSold().getNameState())
                && !"VISA_PLATINUM_SIMULATED".equals(purchase.getPaymentMethod())) {
            throw new ProductUnavailableException("Este pedido ya tiene un pago registrado");
        }
        var response = decide(id, true, amount);
        if (purchase.getPaymentReference() == null) {
            purchase.setPaymentMethod("VISA_PLATINUM_SIMULATED");
            purchase.setPaidAt(java.time.LocalDateTime.now());
            purchase.setPaymentReference("SIM-" + java.util.UUID.randomUUID());
        }
        return response;
    }

    private PurchaseResponse decide(Long id, boolean accepted, BigDecimal amount) {
        var purchase = purchases.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Venta no encontrada con id: " + id));
        String target = accepted ? "PAGADO" : "CANCELADO";
        String current = purchase.getStateSold().getNameState();
        if (accepted) {
            if (purchase.getTotalAmount() == null) {
                throw new ProductUnavailableException("Esta venta anterior no tiene un total verificado");
            }
            if (amount == null || amount.compareTo(purchase.getTotalAmount()) != 0) {
                throw new ProductUnavailableException("El monto del pago no coincide con el total de la venta: "
                        + purchase.getTotalAmount());
            }
        }
        if (target.equalsIgnoreCase(current)) {
            if (accepted) {
                userProducts.assign(purchase);
            }
            return mapper.toPurchaseResponse(purchase);
        }
        if (!"PENDIENTE".equalsIgnoreCase(current)) {
            throw new ProductUnavailableException("El pago ya fue resuelto como " + current);
        }
        if (purchase.getItems().isEmpty() ? purchase.getStockDeductionId() == null
                : purchase.getItems().stream().anyMatch(item -> item.getStockDeductionId() == null)) {
            throw new ProductUnavailableException("Esta compra anterior no tiene un descuento de stock asociado");
        }
        purchase.setStateSold(states.findFirstByNameStateIgnoreCase(target)
                .orElseThrow(() -> new ResourceNotFoundException("Estado no encontrado: " + target)));
        if (accepted) {
            userProducts.assign(purchase);
        } else {
            purchase.setOrderStatus(pe.edu.cibertec.platonmicroservicepayments.model.OrderStatus.CANCELADO);
            if (purchase.getItems().isEmpty()) {
                outbox.enqueue(mapper.toResponse(purchase), purchase.getStockDeductionId(), "payment.rejected");
            } else {
                for (var item : purchase.getItems()) {
                    outbox.enqueue(mapper.toItemResponse(purchase, item), item.getStockDeductionId(), "payment.rejected");
                }
            }
        }
        return mapper.toPurchaseResponse(purchase);
    }
}
