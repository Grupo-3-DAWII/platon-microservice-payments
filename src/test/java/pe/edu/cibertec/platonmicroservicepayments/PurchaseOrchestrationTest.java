package pe.edu.cibertec.platonmicroservicepayments;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import java.util.Map;
import pe.edu.cibertec.platonmicroservicepayments.repository.ProductPriceRepository;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pe.edu.cibertec.platonmicroservicepayments.dto.ProductsSoldRequest;
import pe.edu.cibertec.platonmicroservicepayments.event.PurchaseAvailabilityClient;
import pe.edu.cibertec.platonmicroservicepayments.exception.ProductUnavailableException;
import pe.edu.cibertec.platonmicroservicepayments.exception.ResourceNotFoundException;
import pe.edu.cibertec.platonmicroservicepayments.mapper.ProductsSoldMapper;
import pe.edu.cibertec.platonmicroservicepayments.model.StateSold;
import pe.edu.cibertec.platonmicroservicepayments.repository.ProductsSoldListRepository;
import pe.edu.cibertec.platonmicroservicepayments.repository.ProductsSoldRepository;
import pe.edu.cibertec.platonmicroservicepayments.repository.StateSoldRepository;
import pe.edu.cibertec.platonmicroservicepayments.service.impl.ProductsSoldServiceImpl;
import pe.edu.cibertec.platonmicroservicepayments.service.impl.PurchaseRegistrationService;
import pe.edu.cibertec.platonmicroservicepayments.service.impl.PaymentDecisionService;
import pe.edu.cibertec.platonmicroservicepayments.service.impl.StockReturnCompensationService;
import java.util.UUID;

import java.util.Optional;
import java.util.List;
import java.math.BigDecimal;
import java.util.ArrayList;
import pe.edu.cibertec.platonmicroservicepayments.dto.PurchaseRequest;
import pe.edu.cibertec.platonmicroservicepayments.service.impl.PurchaseStockDeduction;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PurchaseOrchestrationTest {
    @Mock private ProductsSoldRepository repository;
    @Mock private ProductsSoldListRepository listRepository;
    @Mock private StateSoldRepository states;
    @Mock private ProductsSoldMapper mapper;
    @Mock private PurchaseAvailabilityClient availability;
    @Mock private PurchaseRegistrationService registration;
    @Mock private PaymentDecisionService decision;
    @Mock private StockReturnCompensationService compensation;
    @Mock private ProductPriceRepository prices;
    @InjectMocks private ProductsSoldServiceImpl service;
    private final ProductsSoldRequest request = new ProductsSoldRequest(1L, 2L, 1, 1L);

    @BeforeEach
    void catalogPrices() {
        lenient().when(prices.findUnitPrices(anyList())).thenReturn(Map.of(
                1L, new BigDecimal("10.00"), 3L, new BigDecimal("10.00")));
    }

    @Test
    void persistsOnlyAfterProductsApproves() {
        when(states.findById(1L)).thenReturn(Optional.of(StateSold.builder().nameState("PENDIENTE").build()));
        service.create(request);
        var order = inOrder(availability, registration);
        order.verify(availability).requireAvailable(eq(request), any(UUID.class));
        order.verify(registration).register(eq(request), any(UUID.class));
    }

    @Test
    void doesNotRegisterRejectedPurchase() {
        when(states.findById(1L)).thenReturn(Optional.of(StateSold.builder().nameState("PENDIENTE").build()));
        doThrow(new ProductUnavailableException("sin stock")).when(availability).requireAvailable(eq(request), any(UUID.class));
        assertThrows(ProductUnavailableException.class, () -> service.create(request));
        verifyNoInteractions(registration);
        verify(compensation).enqueue(eq(request), any(UUID.class));
    }

    @Test
    void rejectsUnknownStateBeforePublishingRequest() {
        assertThrows(ResourceNotFoundException.class, () -> service.create(request));
        verifyNoInteractions(availability, registration);
    }

    private PurchaseRequest cart() {
        return new PurchaseRequest(2L, 1L, List.of(
                new PurchaseRequest.Item(1L, 2), new PurchaseRequest.Item(3L, 1)), new BigDecimal("30.00"));
    }

    @Test
    void validatesAllProductsBeforeSavingCartWithDistinctDeductionIds() {
        when(states.findById(1L)).thenReturn(Optional.of(StateSold.builder().nameState("PENDIENTE").build()));
        var attempts = new ArrayList<PurchaseStockDeduction>();
        doAnswer(invocation -> {
            attempts.add(new PurchaseStockDeduction(invocation.getArgument(0), invocation.getArgument(1), new BigDecimal("10.00")));
            return null;
        }).when(availability).requireAvailable(any(), any());
        when(registration.registerAll(anyList())).thenAnswer(invocation -> {
            assertEquals(2, attempts.size());
            assertEquals(attempts, invocation.getArgument(0));
            return null;
        });

        service.createMultiple(cart());

        assertEquals(new ProductsSoldRequest(1L, 2L, 2, 1L), attempts.get(0).request());
        assertEquals(new ProductsSoldRequest(3L, 2L, 1, 1L), attempts.get(1).request());
        assertNotEquals(attempts.get(0).deductionId(), attempts.get(1).deductionId());
        verify(registration).registerAll(attempts);
        verifyNoInteractions(compensation);
    }

    @Test
    void compensatesEveryAttemptIncludingRejectedProductAndDoesNotSaveCart() {
        when(states.findById(1L)).thenReturn(Optional.of(StateSold.builder().nameState("PENDIENTE").build()));
        var attempts = new ArrayList<PurchaseStockDeduction>();
        doAnswer(invocation -> {
            attempts.add(new PurchaseStockDeduction(invocation.getArgument(0), invocation.getArgument(1), new BigDecimal("10.00")));
            if (attempts.size() == 2) throw new ProductUnavailableException("sin stock");
            return null;
        }).when(availability).requireAvailable(any(), any());

        assertThrows(ProductUnavailableException.class, () -> service.createMultiple(cart()));

        verifyNoInteractions(registration);
        for (var attempt : attempts) verify(compensation).enqueue(attempt.request(), attempt.deductionId());
        verify(compensation, times(2)).enqueue(any(), any());
    }

    @Test
    void compensatesAllProductsWhenDatabaseRegistrationFailsEvenIfOneCompensationFails() {
        when(states.findById(1L)).thenReturn(Optional.of(StateSold.builder().nameState("PENDIENTE").build()));
        var failure = new IllegalStateException("database failure");
        var attempts = new ArrayList<PurchaseStockDeduction>();
        doAnswer(invocation -> {
            attempts.add(new PurchaseStockDeduction(invocation.getArgument(0), invocation.getArgument(1), new BigDecimal("10.00")));
            return null;
        }).when(availability).requireAvailable(any(), any());
        when(registration.registerAll(anyList())).thenThrow(failure);
        doThrow(new IllegalStateException("compensation failure")).doNothing()
                .when(compensation).enqueue(any(), any());

        assertSame(failure, assertThrows(IllegalStateException.class, () -> service.createMultiple(cart())));
        assertEquals(1, failure.getSuppressed().length);
        for (var attempt : attempts) verify(compensation).enqueue(attempt.request(), attempt.deductionId());
    }

    @Test
    void rejectsDuplicateProductsBeforeDeductingStock() {
        when(states.findById(1L)).thenReturn(Optional.of(StateSold.builder().nameState("PENDIENTE").build()));
        var duplicate = new PurchaseRequest(2L, 1L, List.of(
                new PurchaseRequest.Item(1L, 1), new PurchaseRequest.Item(1L, 2)), new BigDecimal("30.00"));
        assertThrows(IllegalArgumentException.class, () -> service.createMultiple(duplicate));
        verifyNoInteractions(availability, registration, compensation);
    }

    @Test
    void rejectsNonPendingCartBeforeDeductingStock() {
        when(states.findById(1L)).thenReturn(Optional.of(StateSold.builder().nameState("PAGADO").build()));
        assertThrows(IllegalArgumentException.class, () -> service.createMultiple(cart()));
        verifyNoInteractions(availability, registration, compensation);
    }

    @Test
    void rejectsUnderpaymentAndOverpaymentBeforeStockOrDatabaseWrites() {
        when(states.findById(1L)).thenReturn(Optional.of(StateSold.builder().nameState("PENDIENTE").build()));
        for (String amount : List.of("29.99", "30.01")) {
            var original = cart();
            var invalid = new PurchaseRequest(original.idUser(), original.idStateSold(), original.products(),
                    new BigDecimal(amount));
            assertThrows(ProductUnavailableException.class, () -> service.createMultiple(invalid));
        }
        verifyNoInteractions(availability, registration, compensation);
    }

    @Test
    void calculatesDecimalPricesExactlyAndAcceptsEquivalentScale() {
        when(states.findById(1L)).thenReturn(Optional.of(StateSold.builder().nameState("PENDIENTE").build()));
        when(prices.findUnitPrices(anyList())).thenReturn(Map.of(
                1L, new BigDecimal("0.10"), 3L, new BigDecimal("0.20")));
        var original = cart();
        var priced = new PurchaseRequest(2L, 1L, original.products(), new BigDecimal("0.4"));
        service.createMultiple(priced);
        verify(registration).registerAll(argThat(lines -> lines.size() == 2
                && lines.get(0).unitPrice().equals(new BigDecimal("0.10"))
                && lines.get(1).unitPrice().equals(new BigDecimal("0.20"))));
    }

    @Test
    void missingCatalogPriceDoesNotDeductStockOrCreateSale() {
        when(states.findById(1L)).thenReturn(Optional.of(StateSold.builder().nameState("PENDIENTE").build()));
        when(prices.findUnitPrices(anyList())).thenReturn(Map.of());
        assertThrows(ProductUnavailableException.class, () -> service.createMultiple(cart()));
        verifyNoInteractions(availability, registration, compensation);
    }
}
