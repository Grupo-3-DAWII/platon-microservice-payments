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
import java.math.BigDecimal;
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
        service.accept(1L, new BigDecimal("10.00"));
        assertEquals("PAGADO", purchase.getStateSold().getNameState());
        verify(userProducts).assign(purchase);
        verifyNoInteractions(outbox);
        service.accept(1L, new BigDecimal("10.00"));
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
        assertThrows(ProductUnavailableException.class, () -> service.accept(1L, new BigDecimal("10.00")));
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
        assertThrows(ResourceNotFoundException.class, () -> service.accept(1L, new BigDecimal("10.00")));
    }

    @Test
    void incorrectAmountDoesNotChangeStateAssignProductsOrPublishEvents() {
        var purchase = purchase("PENDIENTE");
        when(purchases.findByIdForUpdate(1L)).thenReturn(Optional.of(purchase));
        for (String amount : java.util.List.of("9.99", "10.01")) {
            assertThrows(ProductUnavailableException.class, () -> service.accept(1L, new BigDecimal(amount)));
            assertEquals("PENDIENTE", purchase.getStateSold().getNameState());
        }
        verifyNoInteractions(states, userProducts, outbox);
    }

    @Test
    void repeatedAcceptanceStillRequiresCorrectAmount() {
        var purchase = purchase("PAGADO");
        when(purchases.findByIdForUpdate(1L)).thenReturn(Optional.of(purchase));
        assertThrows(ProductUnavailableException.class, () -> service.accept(1L, new BigDecimal("0.01")));
        verifyNoInteractions(userProducts, outbox);
    }

    @Test
    void historicalSaleWithUnknownTotalCannotBeConfirmedWithAnInventedAmount() {
        var purchase = purchase("PENDIENTE");
        purchase.setTotalAmount(null);
        when(purchases.findByIdForUpdate(1L)).thenReturn(Optional.of(purchase));
        assertThrows(ProductUnavailableException.class, () -> service.accept(1L, BigDecimal.ZERO));
        verifyNoInteractions(states, userProducts, outbox);
    }

    @Test
    void simulatedCardRecordsOnePaymentAndRejectsOtherCustomersOrTokens() {
        var purchase = purchase("PENDIENTE");
        when(purchases.findByIdForUpdate(1L)).thenReturn(Optional.of(purchase));
        when(states.findFirstByNameStateIgnoreCase("PAGADO")).thenReturn(Optional.of(state("PAGADO")));
        assertThrows(ProductUnavailableException.class, () -> service.simulateCard(1L, 2L, new BigDecimal("10"), "invalid"));
        assertThrows(ResourceNotFoundException.class, () -> service.simulateCard(1L, 99L, new BigDecimal("10"), "visa-platinum-test"));
        assertThrows(ProductUnavailableException.class, () -> service.simulateCard(1L, 2L, new BigDecimal("9.99"), "visa-platinum-test"));
        assertEquals("PENDIENTE", purchase.getStateSold().getNameState());
        assertNull(purchase.getPaymentReference());
        service.simulateCard(1L, 2L, new BigDecimal("10"), "visa-platinum-test");
        assertEquals("PAGADO", purchase.getStateSold().getNameState());
        assertEquals("VISA_PLATINUM_SIMULATED", purchase.getPaymentMethod());
        assertNotNull(purchase.getPaidAt());
        var reference = purchase.getPaymentReference();
        var date = purchase.getPaidAt();
        service.simulateCard(1L, 2L, new BigDecimal("10"), "visa-platinum-test");
        assertEquals(reference, purchase.getPaymentReference());
        assertEquals(date, purchase.getPaidAt());
        verifyNoInteractions(outbox);
    }

    private ProductsSold purchase(String status) {
        return ProductsSold.builder().idProductsSold(1L).idProducts(1L).idUser(2L).numberSold(1)
                .stockDeductionId(UUID.randomUUID()).totalAmount(new BigDecimal("10.00")).stateSold(state(status)).build();
    }

    private StateSold state(String name) {
        return StateSold.builder().nameState(name).build();
    }
}
