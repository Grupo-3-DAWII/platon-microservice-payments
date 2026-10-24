package pe.edu.cibertec.platonmicroservicepayments.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pe.edu.cibertec.platonmicroservicepayments.dto.OrderStatusResponse;
import pe.edu.cibertec.platonmicroservicepayments.exception.ProductUnavailableException;
import pe.edu.cibertec.platonmicroservicepayments.exception.ResourceNotFoundException;
import pe.edu.cibertec.platonmicroservicepayments.model.OrderStatus;
import pe.edu.cibertec.platonmicroservicepayments.repository.OrderActorRepository;
import pe.edu.cibertec.platonmicroservicepayments.repository.ProductsSoldRepository;

@Service
@RequiredArgsConstructor
public class OrderFulfillmentService {
    private final ProductsSoldRepository purchases;
    private final OrderActorRepository actors;

    @Transactional
    public OrderStatusResponse update(Long id, Long actorId, OrderStatus target) {
        if (!actors.canManageOrders(actorId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo el personal puede actualizar pedidos");
        }
        var purchase = purchases.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido no encontrado"));
        if (!"PAGADO".equalsIgnoreCase(purchase.getStateSold().getNameState())) {
            throw new ProductUnavailableException("El pedido debe estar pagado antes de cambiar su estado");
        }
        var current = purchase.getOrderStatus();
        if (target == current) return new OrderStatusResponse(id, current);
        if (target != OrderStatus.PENDIENTE && target != OrderStatus.EN_CAMINO && target != OrderStatus.ENTREGADO) {
            throw new ProductUnavailableException("Selecciona Pendiente, En camino o Entregado");
        }
        purchase.setOrderStatus(target);
        return new OrderStatusResponse(id, target);
    }
}
