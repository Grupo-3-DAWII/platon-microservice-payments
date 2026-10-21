package pe.edu.cibertec.platonmicroservicepayments.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.cibertec.platonmicroservicepayments.dto.ProductsSoldResponse;
import pe.edu.cibertec.platonmicroservicepayments.exception.ProductUnavailableException;
import pe.edu.cibertec.platonmicroservicepayments.exception.ResourceNotFoundException;
import pe.edu.cibertec.platonmicroservicepayments.mapper.ProductsSoldMapper;
import pe.edu.cibertec.platonmicroservicepayments.repository.ProductsSoldRepository;
import pe.edu.cibertec.platonmicroservicepayments.repository.PurchaseOutboxRepository;
import pe.edu.cibertec.platonmicroservicepayments.repository.StateSoldRepository;
import pe.edu.cibertec.platonmicroservicepayments.repository.UserProductsRepository;

@Service
@RequiredArgsConstructor
public class PaymentDecisionService {
    private final ProductsSoldRepository purchases;
    private final StateSoldRepository states;
    private final ProductsSoldMapper mapper;
    private final UserProductsRepository userProducts;
    private final PurchaseOutboxRepository outbox;

    @Transactional
    public ProductsSoldResponse accept(Long id) {
        return decide(id, true);
    }

    @Transactional
    public ProductsSoldResponse reject(Long id) {
        return decide(id, false);
    }

    private ProductsSoldResponse decide(Long id, boolean accepted) {
        var purchase = purchases.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Venta no encontrada con id: " + id));
        String target = accepted ? "PAGADO" : "CANCELADO";
        String current = purchase.getStateSold().getNameState();
        if (target.equalsIgnoreCase(current)) {
            if (accepted) {
                userProducts.assign(purchase);
            }
            return mapper.toResponse(purchase);
        }
        if (!"PENDIENTE".equalsIgnoreCase(current)) {
            throw new ProductUnavailableException("El pago ya fue resuelto como " + current);
        }
        if (purchase.getStockDeductionId() == null) {
            throw new ProductUnavailableException("Esta compra anterior no tiene un descuento de stock asociado");
        }
        purchase.setStateSold(states.findFirstByNameStateIgnoreCase(target)
                .orElseThrow(() -> new ResourceNotFoundException("Estado no encontrado: " + target)));
        if (accepted) {
            userProducts.assign(purchase);
        } else {
            outbox.enqueue(mapper.toResponse(purchase), purchase.getStockDeductionId(), "payment.rejected");
        }
        return mapper.toResponse(purchase);
    }
}
