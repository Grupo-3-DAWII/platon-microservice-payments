package pe.edu.cibertec.platonmicroservicepayments.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.cibertec.platonmicroservicepayments.config.PurchaseMessagingProperties;
import pe.edu.cibertec.platonmicroservicepayments.repository.PurchaseOutboxRepository;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.rabbitmq.purchase.publisher-enabled", havingValue = "true", matchIfMissing = true)
public class PurchaseEventProducer {

    private final PurchaseOutboxRepository outbox;
    private final RabbitTemplate rabbitTemplate;
    private final RabbitAdmin rabbitAdmin;
    private final PurchaseMessagingProperties properties;

    @Scheduled(fixedDelayString = "${app.rabbitmq.purchase.publish-delay-ms:5000}", initialDelay = 1000)
    @Transactional
    public void publishPending() {
        try {
            rabbitAdmin.initialize();
        } catch (Exception ex) {
            log.warn("RabbitMQ no disponible; los eventos de compra permanecen pendientes: {}", ex.getMessage());
            return;
        }
        for (var event : outbox.lockPending()) {
            try {
                var correlation = new CorrelationData(event.eventId().toString());
                var message = MessageBuilder.withBody(event.payload().getBytes(StandardCharsets.UTF_8))
                        .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                        .setContentEncoding(StandardCharsets.UTF_8.name())
                        .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                        .setMessageId(event.eventId().toString())
                        .setType(event.eventType())
                        .build();
                String routingKey = "purchase.created".equals(event.eventType())
                        ? properties.routingKey() : event.eventType();
                rabbitTemplate.send(properties.exchange(), routingKey, message, correlation);
                var confirm = correlation.getFuture().get(5, TimeUnit.SECONDS);
                if (!confirm.ack() || correlation.getReturned() != null) {
                    throw new IllegalStateException("RabbitMQ no confirmo el enrutamiento del evento");
                }
            } catch (Exception ex) {
                outbox.markFailed(event.eventId(), ex.toString());
                log.warn("Se reintentara el evento de compra {}: {}", event.eventId(), ex.getMessage());
                if (ex instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                break;
            }
            outbox.markPublished(event.eventId());
            log.info("Evento de compra publicado: {}", event.eventId());
        }
    }
}
