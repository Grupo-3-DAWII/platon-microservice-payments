package pe.edu.cibertec.platonmicroservicepayments.event;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import pe.edu.cibertec.platonmicroservicepayments.config.PurchaseMessagingProperties;
import pe.edu.cibertec.platonmicroservicepayments.repository.PurchaseOutboxRepository;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PurchaseEventProducerTest {

    private final PurchaseOutboxRepository outbox = mock(PurchaseOutboxRepository.class);
    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final RabbitAdmin rabbitAdmin = mock(RabbitAdmin.class);
    private final PurchaseMessagingProperties properties = new PurchaseMessagingProperties(
            "platon.purchase.events", "purchase.created", "platon.purchase.created");
    private final UUID eventId = UUID.randomUUID();
    private final PurchaseEventProducer producer = new PurchaseEventProducer(
            outbox, rabbitTemplate, rabbitAdmin, properties);

    @BeforeEach
    void setUp() {
        when(outbox.lockPending()).thenReturn(List.of(
                new PurchaseOutboxRepository.PendingEvent(eventId, "{\"eventType\":\"purchase.created\"}")));
    }

    @Test
    void marksPublishedOnlyAfterBrokerConfirmsPersistentJson() {
        doAnswer(invocation -> {
            Message message = invocation.getArgument(2);
            assertEquals("application/json", message.getMessageProperties().getContentType());
            assertEquals(MessageDeliveryMode.PERSISTENT, message.getMessageProperties().getDeliveryMode());
            assertEquals(eventId.toString(), message.getMessageProperties().getMessageId());
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).send(eq(properties.exchange()), eq(properties.routingKey()),
                any(Message.class), any(CorrelationData.class));

        producer.publishPending();

        verify(outbox).markPublished(eventId);
        verify(outbox, never()).markFailed(any(), anyString());
    }

    @Test
    void unroutableMessageRemainsPendingEvenWhenExchangeAcknowledgesIt() {
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.setReturned(new ReturnedMessage(invocation.getArgument(2), 312,
                    "NO_ROUTE", properties.exchange(), properties.routingKey()));
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));

        producer.publishPending();

        verify(outbox).markFailed(eq(eventId), contains("no confirmo"));
        verify(outbox, never()).markPublished(any());
    }

    @Test
    void brokerFailureKeepsEventForRetry() {
        doThrow(new IllegalStateException("broker unavailable")).when(rabbitTemplate)
                .send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));

        producer.publishPending();

        verify(outbox).markFailed(eq(eventId), contains("broker unavailable"));
        verify(outbox, never()).markPublished(any());
    }
}
