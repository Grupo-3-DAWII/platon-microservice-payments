package pe.edu.cibertec.platonmicroservicepayments.config;

import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
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
    public Declarables purchaseTopology(PurchaseMessagingProperties properties) {
        TopicExchange exchange = new TopicExchange(properties.exchange(), true, false);
        Queue queue = new Queue(properties.queue(), true);
        Queue rejected = new Queue("platon.products.payment.rejected", true);
        return new Declarables(exchange, queue, rejected,
                BindingBuilder.bind(queue).to(exchange).with(properties.routingKey()),
                BindingBuilder.bind(rejected).to(exchange).with("payment.rejected"));
    }

    @Bean
    public Queue purchaseAvailabilityReplyQueue() {
        return new Queue("purchase.availability.checked." + UUID.randomUUID(), false, true, true);
    }

    @Bean
    public Declarables availabilityTopology(PurchaseValidationProperties properties,
            @Qualifier("purchaseAvailabilityReplyQueue") Queue replyQueue) {
        TopicExchange exchange = new TopicExchange(properties.exchange(), true, false);
        Queue requests = new Queue(properties.queue(), true, false, false, Map.of("x-message-ttl", 30000));
        return new Declarables(exchange, requests,
                BindingBuilder.bind(requests).to(exchange).with(properties.routingKey()),
                BindingBuilder.bind(replyQueue).to(exchange).with(replyQueue.getName()));
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
