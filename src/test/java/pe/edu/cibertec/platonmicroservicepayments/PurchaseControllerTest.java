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
    void acceptsMultipleProductsAndReturnsEachSale() throws Exception {
        var request = new PurchaseRequest(2L, 1L, List.of(
                new PurchaseRequest.Item(10L, 2), new PurchaseRequest.Item(20L, 1)));
        when(service.createMultiple(request)).thenReturn(List.of(
                new ProductsSoldResponse(100L, 10L, 2L, 2, null, new StateSoldResponse(1L, "PENDIENTE")),
                new ProductsSoldResponse(101L, 20L, 2L, 1, null, new StateSoldResponse(1L, "PENDIENTE"))));
        mvc.perform(post("/api/v1/products-sold").contentType(MediaType.APPLICATION_JSON).content("""
                {"idUser":2,"idStateSold":1,"products":[
                  {"idProducts":10,"numberSold":2},{"idProducts":20,"numberSold":1}]}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].idProductsSold").value(100))
                .andExpect(jsonPath("$[1].idProductsSold").value(101));
        verify(service).createMultiple(request);
    }

    @Test
    void rejectsMissingEmptyOrInvalidProductsWithoutCallingService() throws Exception {
        for (String products : List.of("null", "[]", "[null]",
                "[{\"idProducts\":1,\"numberSold\":0}]",
                "[{\"numberSold\":1}]", "[{\"idProducts\":-1,\"numberSold\":1}]")) {
            mvc.perform(post("/api/v1/products-sold").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"idUser\":2,\"idStateSold\":1,\"products\":" + products + "}"))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }
}
