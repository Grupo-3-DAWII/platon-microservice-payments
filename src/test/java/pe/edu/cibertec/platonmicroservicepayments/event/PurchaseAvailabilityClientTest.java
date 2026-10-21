package pe.edu.cibertec.platonmicroservicepayments.event;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import pe.edu.cibertec.platonmicroservicepayments.config.PurchaseValidationProperties;
import pe.edu.cibertec.platonmicroservicepayments.dto.ProductsSoldRequest;
import pe.edu.cibertec.platonmicroservicepayments.exception.ProductUnavailableException;
import pe.edu.cibertec.platonmicroservicepayments.exception.PurchaseValidationTimeoutException;
import pe.edu.cibertec.platonmicroservicepayments.exception.PurchaseValidationUnavailableException;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PurchaseAvailabilityClientTest {
    private final RabbitTemplate template = mock(RabbitTemplate.class);
    private final JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
    private final PurchaseValidationProperties properties = new PurchaseValidationProperties(
            "platon.purchase.events", "purchase.requested", "platon.products.purchase.validation", 10000);
    private final PurchaseAvailabilityClient client = new PurchaseAvailabilityClient(template, mapper, properties);
    private final ProductsSoldRequest purchase = new ProductsSoldRequest(1L, 2L, 1, 1L);

    @Test
    void acceptsOnlyMatchingPositiveReply() {
        when(template.sendAndReceive(eq(properties.exchange()), eq(properties.routingKey()), any(Message.class)))
                .thenAnswer(invocation -> {
                    Message message = invocation.getArgument(2);
                    var request = mapper.readValue(message.getBody(), PurchaseAvailabilityRequest.class);
                    assertEquals("10000", message.getMessageProperties().getExpiration());
                    assertEquals("purchase.requested", request.eventType());
                    return reply(new PurchaseAvailabilityResponse(request.requestId(), "purchase.availability.checked",
                            1L, 1, true, "AVAILABLE"));
                });
        assertDoesNotThrow(() -> client.requireAvailable(purchase));
    }

    @ParameterizedTest
    @CsvSource({
            "PRODUCT_NOT_FOUND, El producto solicitado no existe",
            "PRODUCT_INACTIVE, El producto solicitado no esta activo",
            "INSUFFICIENT_STOCK, No hay stock suficiente para la cantidad solicitada",
            "REQUEST_EXPIRED, La solicitud de validacion de disponibilidad expiro",
            "INVALID_REQUEST, El producto no esta disponible para la compra"
    })
    void rejectsNegativeReply(String reason, String expectedMessage) {
        when(template.sendAndReceive(anyString(), anyString(), any(Message.class))).thenAnswer(invocation -> {
            var request = mapper.readValue(((Message) invocation.getArgument(2)).getBody(), PurchaseAvailabilityRequest.class);
            return reply(new PurchaseAvailabilityResponse(request.requestId(), "purchase.availability.checked",
                    1L, 1, false, reason));
        });
        assertEquals(expectedMessage, assertThrows(ProductUnavailableException.class,
                () -> client.requireAvailable(purchase)).getMessage());
    }

    @Test
    void rejectsTimeoutAndBrokerFailure() {
        assertThrows(PurchaseValidationTimeoutException.class, () -> client.requireAvailable(purchase));
        when(template.sendAndReceive(anyString(), anyString(), any(Message.class)))
                .thenThrow(new AmqpConnectException(new IllegalStateException("offline")));
        assertThrows(PurchaseValidationUnavailableException.class, () -> client.requireAvailable(purchase));
    }

    @Test
    void rejectsMalformedOrUnrelatedReply() {
        when(template.sendAndReceive(anyString(), anyString(), any(Message.class)))
                .thenReturn(MessageBuilder.withBody(new byte[]{0}).build());
        assertThrows(PurchaseValidationUnavailableException.class, () -> client.requireAvailable(purchase));
        when(template.sendAndReceive(anyString(), anyString(), any(Message.class)))
                .thenReturn(reply(new PurchaseAvailabilityResponse(UUID.randomUUID(), "purchase.availability.checked",
                        1L, 1, true, "AVAILABLE")));
        assertThrows(PurchaseValidationUnavailableException.class, () -> client.requireAvailable(purchase));
    }

    private Message reply(PurchaseAvailabilityResponse response) {
        return MessageBuilder.withBody(mapper.writeValueAsBytes(response)).build();
    }
}
