package pe.edu.cibertec.platonmicroservicepayments.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rabbitmq.validation")
public record PurchaseValidationProperties(String exchange, String routingKey, String queue, long timeoutMs) {}
