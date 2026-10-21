package pe.edu.cibertec.platonmicroservicepayments.event;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import pe.edu.cibertec.platonmicroservicepayments.config.PurchaseValidationProperties;
import pe.edu.cibertec.platonmicroservicepayments.dto.ProductsSoldRequest;
import pe.edu.cibertec.platonmicroservicepayments.exception.ProductUnavailableException;
import pe.edu.cibertec.platonmicroservicepayments.exception.PurchaseValidationTimeoutException;
import pe.edu.cibertec.platonmicroservicepayments.exception.PurchaseValidationUnavailableException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PurchaseAvailabilityClient {

    private final RabbitTemplate rabbitTemplate;
    private final JsonMapper jsonMapper;
    private final PurchaseValidationProperties properties;

    public void requireAvailable(ProductsSoldRequest purchase) {
        requireAvailable(purchase, UUID.randomUUID());
    }

    public void requireAvailable(ProductsSoldRequest purchase, UUID requestId) {
        var request = new PurchaseAvailabilityRequest(requestId, "purchase.requested",
                purchase.idProducts(), purchase.numberSold(), Instant.now().plusMillis(properties.timeoutMs()));
        PurchaseAvailabilityResponse response;
        try {
            var message = MessageBuilder.withBody(jsonMapper.writeValueAsBytes(request))
                    .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                    .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                    .setMessageId(requestId.toString())
                    .setType(request.eventType())
                    .setExpiration(Long.toString(properties.timeoutMs()))
                    .build();
            var reply = rabbitTemplate.sendAndReceive(properties.exchange(), properties.routingKey(), message);
            if (reply == null) {
                throw new PurchaseValidationTimeoutException();
            }
            response = jsonMapper.readValue(reply.getBody(), PurchaseAvailabilityResponse.class);
        } catch (AmqpException | JacksonException ex) {
            throw new PurchaseValidationUnavailableException("No se pudo validar la compra mediante RabbitMQ", ex);
        }
        if (response == null || !requestId.equals(response.requestId())
                || !"purchase.availability.checked".equals(response.eventType())
                || !Objects.equals(purchase.idProducts(), response.idProducts())
                || !Objects.equals(purchase.numberSold(), response.numberSold())) {
            throw new PurchaseValidationUnavailableException("Products devolvio una respuesta de validacion invalida", null);
        }
        if (!response.available()) {
            throw new ProductUnavailableException(switch (String.valueOf(response.reason())) {
                case "PRODUCT_NOT_FOUND" -> "El producto solicitado no existe";
                case "PRODUCT_INACTIVE" -> "El producto solicitado no esta activo";
                case "INSUFFICIENT_STOCK" -> "No hay stock suficiente para la cantidad solicitada";
                case "REQUEST_EXPIRED" -> "La solicitud de validacion de disponibilidad expiro";
                default -> "El producto no esta disponible para la compra";
            });
        }
    }
}
