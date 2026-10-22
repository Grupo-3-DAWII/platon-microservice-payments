package pe.edu.cibertec.platonmicroservicepayments;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import pe.edu.cibertec.platonmicroservicepayments.controller.ProductsSoldController;
import pe.edu.cibertec.platonmicroservicepayments.dto.*;
import pe.edu.cibertec.platonmicroservicepayments.exception.GlobalExceptionHandler;
import pe.edu.cibertec.platonmicroservicepayments.service.ProductsSoldService;
import java.util.List;
import java.math.BigDecimal;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PurchaseControllerTest {
    private ProductsSoldService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(ProductsSoldService.class);
        mvc = MockMvcBuilders.standaloneSetup(new ProductsSoldController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void acceptsMultipleProductsAndReturnsOneSale() throws Exception {
        var request = new PurchaseRequest(2L, 1L, List.of(
                new PurchaseRequest.Item(10L, 2), new PurchaseRequest.Item(20L, 1)), new BigDecimal("30.00"));
        when(service.createMultiple(request)).thenReturn(new PurchaseResponse(100L, 2L, null,
                new StateSoldResponse(1L, "PENDIENTE"), List.of(
                new PurchaseResponse.Item(10L, 2, null, null), new PurchaseResponse.Item(20L, 1, null, null)), new BigDecimal("30.00")));
        mvc.perform(post("/api/v1/products-sold").contentType(MediaType.APPLICATION_JSON).content("""
                {"idUser":2,"idStateSold":1,"totalAmount":30.00,"products":[
                  {"idProducts":10,"numberSold":2},{"idProducts":20,"numberSold":1}]}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products.length()").value(2))
                .andExpect(jsonPath("$.idProductsSold").value(100))
                .andExpect(jsonPath("$.totalAmount").value(30.00))
                .andExpect(jsonPath("$.products[1].idProducts").value(20));
        verify(service).createMultiple(request);
    }

    @Test
    void rejectsMissingEmptyOrInvalidProductsWithoutCallingService() throws Exception {
        for (String products : List.of("null", "[]", "[null]",
                "[{\"idProducts\":1,\"numberSold\":0}]",
                "[{\"numberSold\":1}]", "[{\"idProducts\":-1,\"numberSold\":1}]")) {
            mvc.perform(post("/api/v1/products-sold").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"idUser\":2,\"idStateSold\":1,\"totalAmount\":10.00,\"products\":" + products + "}"))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }

    @Test
    void paymentEndpointsReturnOneSaleWithAllItsProducts() throws Exception {
        var products = List.of(new PurchaseResponse.Item(10L, 1, null, null), new PurchaseResponse.Item(20L, 2, null, null),
                new PurchaseResponse.Item(30L, 3, null, null));
        when(service.acceptPayment(100L, new PaymentRequest(new BigDecimal("60.00")))).thenReturn(new PurchaseResponse(100L, 2L, null,
                new StateSoldResponse(2L, "PAGADO"), products, new BigDecimal("60.00")));
        when(service.rejectPayment(100L)).thenReturn(new PurchaseResponse(100L, 2L, null,
                new StateSoldResponse(3L, "CANCELADO"), products, new BigDecimal("60.00")));
        for (String action : List.of("accept", "reject")) {
            mvc.perform(post("/api/v1/products-sold/100/payment/" + action)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":60.00}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.idProductsSold").value(100))
                    .andExpect(jsonPath("$.products.length()").value(3))
                    .andExpect(jsonPath("$.stateSold.nameState").value(action.equals("accept") ? "PAGADO" : "CANCELADO"));
        }
    }

    @Test
    void rejectsMissingNegativeExcessiveOrOverPreciseAmounts() throws Exception {
        for (String amount : List.of("null", "-1.00", "1.001", "10000000000000000.00")) {
            mvc.perform(post("/api/v1/products-sold/100/payment/accept").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"amount\":" + amount + "}"))
                    .andExpect(status().isBadRequest());
            mvc.perform(post("/api/v1/products-sold").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"idUser\":2,\"idStateSold\":1,\"products\":[{\"idProducts\":10,\"numberSold\":1}],\"totalAmount\":" + amount + "}"))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/v1/products-sold/100/payment/accept"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void incorrectPaymentAmountReturnsConflict() throws Exception {
        when(service.acceptPayment(100L, new PaymentRequest(new BigDecimal("0.01"))))
                .thenThrow(new pe.edu.cibertec.platonmicroservicepayments.exception.ProductUnavailableException(
                        "El monto del pago no coincide con el total de la venta"));
        mvc.perform(post("/api/v1/products-sold/100/payment/accept").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":0.01}"))
                .andExpect(status().isConflict());
    }
}
