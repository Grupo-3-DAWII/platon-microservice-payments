package pe.edu.cibertec.platonmicroservicepayments.event;

import java.time.Instant;
import java.util.UUID;

public record PurchaseAvailabilityRequest(
        UUID requestId, String eventType, Long idProducts, Integer numberSold, Instant expiresAt
) {}
