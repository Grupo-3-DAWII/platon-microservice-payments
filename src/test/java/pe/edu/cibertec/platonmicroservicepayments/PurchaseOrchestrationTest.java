package pe.edu.cibertec.platonmicroservicepayments;

import org.junit.jupiter.api.Test;
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

import static org.junit.jupiter.api.Assertions.assertThrows;
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
    @InjectMocks private ProductsSoldServiceImpl service;
    private final ProductsSoldRequest request = new ProductsSoldRequest(1L, 2L, 1, 1L);

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
}
