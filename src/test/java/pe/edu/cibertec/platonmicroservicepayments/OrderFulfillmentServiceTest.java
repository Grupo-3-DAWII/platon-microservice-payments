package pe.edu.cibertec.platonmicroservicepayments;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import pe.edu.cibertec.platonmicroservicepayments.model.*;
import pe.edu.cibertec.platonmicroservicepayments.repository.*;
import pe.edu.cibertec.platonmicroservicepayments.service.impl.OrderFulfillmentService;
import pe.edu.cibertec.platonmicroservicepayments.exception.ProductUnavailableException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderFulfillmentServiceTest {
    @Mock ProductsSoldRepository purchases;
    @Mock OrderActorRepository actors;
    @InjectMocks OrderFulfillmentService service;

    private ProductsSold purchase(String payment) {
        var state = new StateSold(); state.setNameState(payment);
        var sale = ProductsSold.builder().idProductsSold(1L).stateSold(state).build();
        when(actors.canManageOrders(2L)).thenReturn(true);
        when(purchases.findByIdForUpdate(1L)).thenReturn(Optional.of(sale));
        return sale;
    }
    @Test void paidOrderMovesThroughDeliveryWithoutChangingPaymentOrItems() {
        var sale = purchase("PAGADO");
        for (var next : java.util.List.of(OrderStatus.ENTREGADO, OrderStatus.PENDIENTE, OrderStatus.EN_CAMINO)) {
            assertEquals(next, service.update(1L, 2L, next).orderStatus());
            assertEquals("PAGADO", sale.getStateSold().getNameState());
            assertTrue(sale.getItems().isEmpty());
        }
    }
    @Test void repeatedUpdateIsIdempotentAndCanReturnToPending() {
        var sale = purchase("PAGADO");
        service.update(1L,2L,OrderStatus.ENTREGADO);
        service.update(1L,2L,OrderStatus.ENTREGADO);
        service.update(1L,2L,OrderStatus.PENDIENTE);
        assertEquals(OrderStatus.PENDIENTE,sale.getOrderStatus());
    }
    @Test void pendingPaymentCannotBePrepared() {
        var sale=purchase("PENDIENTE");
        assertThrows(ProductUnavailableException.class, () -> service.update(1L,2L,OrderStatus.EN_CAMINO));
        assertEquals(OrderStatus.PENDIENTE,sale.getOrderStatus());
    }
    @Test void paidOrderCannotBeCanceledThroughDeliveryEndpoint() {
        var sale=purchase("PAGADO");
        assertThrows(ProductUnavailableException.class, () -> service.update(1L,2L,OrderStatus.CANCELADO));
        assertEquals(OrderStatus.PENDIENTE,sale.getOrderStatus());
    }
    @Test void deliveredOrderCanBeCorrectedWithoutChangingItsPayment() {
        var sale=purchase("PAGADO");sale.setOrderStatus(OrderStatus.ENTREGADO);
        service.update(1L,2L,OrderStatus.EN_CAMINO);
        assertEquals(OrderStatus.EN_CAMINO,sale.getOrderStatus());
        assertEquals("PAGADO",sale.getStateSold().getNameState());
    }
    @Test void customerOrInactiveActorIsRejectedBeforeLoadingOrder() {
        var error=assertThrows(ResponseStatusException.class, () -> service.update(1L,1001L,OrderStatus.EN_CAMINO));
        assertEquals(403,error.getStatusCode().value());verifyNoInteractions(purchases);
    }
}
