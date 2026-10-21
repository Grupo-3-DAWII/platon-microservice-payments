package pe.edu.cibertec.platonmicroservicepayments.event;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

public record PurchaseCreatedEvent(
        UUID eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        Long idProductsSold,
        Long idProducts,
        Long idUser,
        Integer numberSold,
        LocalDateTime createDate,
        Long idStateSold,
        String state,
        UUID stockDeductionId
) {}
