package pe.edu.cibertec.platonmicroservicepayments.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.Map;
import java.util.UUID;

@Configuration
@EnableScheduling
@EnableConfigurationProperties({PurchaseMessagingProperties.class, PurchaseValidationProperties.class})
public class RabbitMqConfig {

    @Bean
    public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        return new RabbitAdmin(connectionFactory);
    }

    @Bean
    public Queue purchaseAvailabilityReplyQueue() {
        return new Queue("purchase.availability.checked." + UUID.randomUUID(), false, true, true);
    }

    @Bean
    public Binding purchaseAvailabilityReplyBinding(PurchaseValidationProperties properties,
            @Qualifier("purchaseAvailabilityReplyQueue") Queue replyQueue) {
        return new Binding(replyQueue.getName(), Binding.DestinationType.QUEUE,
                properties.exchange(), replyQueue.getName(), Map.of());
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
            PurchaseValidationProperties properties,
            @Qualifier("purchaseAvailabilityReplyQueue") Queue replyQueue) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMandatory(true);
        template.setReplyAddress(properties.exchange() + "/" + replyQueue.getName());
        template.setReplyTimeout(properties.timeoutMs());
        return template;
    }

    @Bean
    public SimpleMessageListenerContainer purchaseAvailabilityReplies(ConnectionFactory connectionFactory,
            RabbitTemplate rabbitTemplate,
            @Qualifier("purchaseAvailabilityReplyQueue") Queue replyQueue) {
        SimpleMessageListenerContainer container = new SimpleMessageListenerContainer(connectionFactory);
        container.setQueues(replyQueue);
        container.setMessageListener(rabbitTemplate);
        container.setDefaultRequeueRejected(false);
        return container;
    }
}
