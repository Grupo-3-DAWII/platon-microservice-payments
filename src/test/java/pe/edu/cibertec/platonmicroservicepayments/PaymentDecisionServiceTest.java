package pe.edu.cibertec.platonmicroservicepayments;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pe.edu.cibertec.platonmicroservicepayments.exception.ProductUnavailableException;
import pe.edu.cibertec.platonmicroservicepayments.exception.ResourceNotFoundException;
import pe.edu.cibertec.platonmicroservicepayments.mapper.ProductsSoldMapper;
import pe.edu.cibertec.platonmicroservicepayments.model.ProductsSold;
import pe.edu.cibertec.platonmicroservicepayments.model.StateSold;
import pe.edu.cibertec.platonmicroservicepayments.repository.*;
import pe.edu.cibertec.platonmicroservicepayments.service.impl.PaymentDecisionService;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentDecisionServiceTest {
    @Mock private ProductsSoldRepository purchases;
    @Mock private StateSoldRepository states;
    @Mock private ProductsSoldMapper mapper;
    @Mock private UserProductsRepository userProducts;
    @Mock private PurchaseOutboxRepository outbox;
    @InjectMocks private PaymentDecisionService service;

    @Test
    void acceptingAssignsProductAndDoesNotPublishAnyStockEvent() {
        var purchase = purchase("PENDIENTE");
        when(purchases.findByIdForUpdate(1L)).thenReturn(Optional.of(purchase));
        when(states.findFirstByNameStateIgnoreCase("PAGADO")).thenReturn(Optional.of(state("PAGADO")));
        service.accept(1L);
        assertEquals("PAGADO", purchase.getStateSold().getNameState());
        verify(userProducts).assign(purchase);
        verifyNoInteractions(outbox);
        service.accept(1L);
        verifyNoInteractions(outbox);
    }

    @Test
    void rejectingPublishesExactlyOneReturnEventEvenWhenRepeated() {
        var purchase = purchase("PENDIENTE");
        when(purchases.findByIdForUpdate(1L)).thenReturn(Optional.of(purchase));
        when(states.findFirstByNameStateIgnoreCase("CANCELADO")).thenReturn(Optional.of(state("CANCELADO")));
        service.reject(1L);
        service.reject(1L);
        assertEquals("CANCELADO", purchase.getStateSold().getNameState());
        verify(outbox, times(1)).enqueue(null, purchase.getStockDeductionId(), "payment.rejected");
        verifyNoInteractions(userProducts);
    }

    @Test
    void paidAndCanceledAreFinalDecisions() {
        when(purchases.findByIdForUpdate(1L)).thenReturn(Optional.of(purchase("PAGADO")));
        assertThrows(ProductUnavailableException.class, () -> service.reject(1L));
        when(purchases.findByIdForUpdate(1L)).thenReturn(Optional.of(purchase("CANCELADO")));
        assertThrows(ProductUnavailableException.class, () -> service.accept(1L));
        verifyNoInteractions(userProducts, outbox);
    }

    @Test
    void legacyPurchaseCannotReturnStockThatWasNeverDeducted() {
        var purchase = purchase("PENDIENTE");
        purchase.setStockDeductionId(null);
        when(purchases.findByIdForUpdate(1L)).thenReturn(Optional.of(purchase));
        assertThrows(ProductUnavailableException.class, () -> service.reject(1L));
        verifyNoInteractions(outbox);
    }

    @Test
    void missingPurchaseIsNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> service.accept(1L));
    }

    private ProductsSold purchase(String status) {
        return ProductsSold.builder().idProductsSold(1L).idProducts(1L).idUser(2L).numberSold(1)
                .stockDeductionId(UUID.randomUUID()).stateSold(state(status)).build();
    }

    private StateSold state(String name) {
        return StateSold.builder().nameState(name).build();
    }
}
