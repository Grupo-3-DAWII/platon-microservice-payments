package pe.edu.cibertec.platonmicroservicepayments.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rabbitmq.purchase")
public record PurchaseMessagingProperties(String exchange, String routingKey, String queue) {}
