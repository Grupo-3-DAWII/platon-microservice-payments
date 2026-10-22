package pe.edu.cibertec.platonmicroservicepayments;

import org.junit.jupiter.api.Test;
import pe.edu.cibertec.platonmicroservicepayments.dto.*;
import pe.edu.cibertec.platonmicroservicepayments.mapper.*;
import pe.edu.cibertec.platonmicroservicepayments.model.*;
import pe.edu.cibertec.platonmicroservicepayments.repository.*;
import pe.edu.cibertec.platonmicroservicepayments.service.impl.*;
import java.time.LocalDateTime;
import java.util.List;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GroupedSaleTest {
    private final ProductsSoldRepository sales = mock(ProductsSoldRepository.class);
    private final StateSoldRepository states = mock(StateSoldRepository.class);
    private final PurchaseOutboxRepository outbox = mock(PurchaseOutboxRepository.class);
    private final UserProductsRepository owned = mock(UserProductsRepository.class);
    private final ProductsSoldMapper mapper = new ProductsSoldMapper(new StateSoldMapper());

    @Test
    void threeProductsCreateOneHeaderAndThreeDetailsAndEventsWithSameSaleId() {
        var pending = StateSold.builder().idStateSold(1L).nameState("PENDIENTE").build();
        when(states.findById(1L)).thenReturn(Optional.of(pending));
        when(sales.saveAndFlush(any())).thenAnswer(call -> {
            ProductsSold sale = call.getArgument(0);
            assertNull(sale.getIdProducts());
            assertNull(sale.getNumberSold());
            assertEquals(3, sale.getItems().size());
            sale.setIdProductsSold(100L);
            sale.setCreateDate(LocalDateTime.now());
            sale.getItems().forEach(item -> assertSame(sale, item.getSale()));
            return sale;
        });
        var deductions = List.of(
                new PurchaseStockDeduction(new ProductsSoldRequest(10L, 2L, 1, 1L), UUID.randomUUID(), new BigDecimal("10.00")),
                new PurchaseStockDeduction(new ProductsSoldRequest(20L, 2L, 2, 1L), UUID.randomUUID(), new BigDecimal("10.00")),
                new PurchaseStockDeduction(new ProductsSoldRequest(30L, 2L, 3, 1L), UUID.randomUUID(), new BigDecimal("10.00")));
        var service = new PurchaseRegistrationService(sales, states, mapper, outbox);
        var response = service.registerAll(deductions);
        assertEquals(100L, response.idProductsSold());
        assertEquals(List.of(new PurchaseResponse.Item(10L, 1, new BigDecimal("10.00"), new BigDecimal("10.00")),
                new PurchaseResponse.Item(20L, 2, new BigDecimal("10.00"), new BigDecimal("20.00")),
                new PurchaseResponse.Item(30L, 3, new BigDecimal("10.00"), new BigDecimal("30.00"))), response.products());
        assertEquals(new BigDecimal("60.00"), response.totalAmount());
        verify(sales, times(1)).saveAndFlush(any());
        for (var deduction : deductions) {
            verify(outbox).enqueue(new ProductsSoldResponse(100L, deduction.request().idProducts(), 2L,
                    deduction.request().numberSold(), response.createDate(), response.stateSold()),
                    deduction.deductionId(), "purchase.created");
        }
        verifyNoMoreInteractions(outbox);
    }

    @Test
    void oneAcceptanceAssignsEntireSaleAndRejectingAfterwardsIsForbidden() {
        var sale = groupedSale();
        when(sales.findByIdForUpdate(100L)).thenReturn(Optional.of(sale));
        when(states.findFirstByNameStateIgnoreCase("PAGADO")).thenReturn(Optional.of(
                StateSold.builder().idStateSold(2L).nameState("PAGADO").build()));
        var service = new PaymentDecisionService(sales, states, mapper, owned, outbox);
        var response = service.accept(100L, new BigDecimal("60.00"));
        assertEquals(3, response.products().size());
        assertEquals("PAGADO", response.stateSold().nameState());
        verify(owned).assign(sale);
        assertEquals(response, service.accept(100L, new BigDecimal("60.00")));
        assertThrows(pe.edu.cibertec.platonmicroservicepayments.exception.ProductUnavailableException.class,
                () -> service.reject(100L));
        verifyNoInteractions(outbox);
    }

    @Test
    void oneRejectionReturnsAllThreeProductsExactlyOnceWithOriginalDeductionIds() {
        var sale = groupedSale();
        when(sales.findByIdForUpdate(100L)).thenReturn(Optional.of(sale));
        when(states.findFirstByNameStateIgnoreCase("CANCELADO")).thenReturn(Optional.of(
                StateSold.builder().idStateSold(3L).nameState("CANCELADO").build()));
        var service = new PaymentDecisionService(sales, states, mapper, owned, outbox);
        var response = service.reject(100L);
        assertEquals(3, response.products().size());
        assertEquals("CANCELADO", response.stateSold().nameState());
        assertEquals(response, service.reject(100L));
        for (var item : sale.getItems()) {
            verify(outbox).enqueue(mapper.toItemResponse(sale, item), item.getStockDeductionId(), "payment.rejected");
        }
        verifyNoMoreInteractions(outbox);
        verifyNoInteractions(owned);
    }

    private ProductsSold groupedSale() {
        var sale = ProductsSold.builder().idProductsSold(100L).idUser(2L).totalAmount(new BigDecimal("60.00")).createDate(LocalDateTime.now())
                .stateSold(StateSold.builder().idStateSold(1L).nameState("PENDIENTE").build()).build();
        for (long productId : List.of(10L, 20L, 30L)) {
            sale.getItems().add(ProductsSoldItem.builder().sale(sale).idProducts(productId).numberSold(2)
                    .unitPrice(new BigDecimal("10.00"))
                    .stockDeductionId(UUID.randomUUID()).build());
        }
        return sale;
    }
}
