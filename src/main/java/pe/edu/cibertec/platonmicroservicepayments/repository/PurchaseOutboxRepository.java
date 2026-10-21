package pe.edu.cibertec.platonmicroservicepayments.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.cibertec.platonmicroservicepayments.dto.ProductsSoldResponse;
import pe.edu.cibertec.platonmicroservicepayments.event.PurchaseCreatedEvent;
import tools.jackson.databind.json.JsonMapper;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class PurchaseOutboxRepository {

    private final JdbcClient jdbcClient;
    private final JsonMapper jsonMapper;

    public PurchaseOutboxRepository(DataSource dataSource, JsonMapper jsonMapper) {
        this.jdbcClient = JdbcClient.create(dataSource);
        this.jsonMapper = jsonMapper;
    }

    public void enqueue(ProductsSoldResponse purchase) {
        enqueue(purchase, null, "purchase.created");
    }

    public void enqueue(ProductsSoldResponse purchase, UUID deductionId, String eventType) {
        UUID eventId = UUID.randomUUID();
        var event = new PurchaseCreatedEvent(eventId, eventType, 1, Instant.now(),
                purchase.idProductsSold(), purchase.idProducts(), purchase.idUser(), purchase.numberSold(),
                purchase.createDate(), purchase.stateSold().idStateSold(), purchase.stateSold().nameState(), deductionId);
        jdbcClient.sql("""
                INSERT INTO payment.purchase_event_outbox (event_id, id_products_sold, payload, event_type, stock_deduction_id)
                VALUES (:eventId, :saleId, :payload, :eventType, :deductionId)
                ON CONFLICT DO NOTHING
                """)
                .param("eventId", eventId)
                .param("saleId", purchase.idProductsSold())
                .param("payload", jsonMapper.writeValueAsString(event))
                .param("eventType", eventType)
                .param("deductionId", deductionId)
                .update();
    }

    public List<PendingEvent> lockPending() {
        return jdbcClient.sql("""
                SELECT event_id, payload, event_type FROM payment.purchase_event_outbox
                WHERE published_at IS NULL
                ORDER BY created_at, event_id
                LIMIT 20 FOR UPDATE SKIP LOCKED
                """)
                .query((rs, rowNum) -> new PendingEvent(rs.getObject("event_id", UUID.class),
                        rs.getString("payload"), rs.getString("event_type")))
                .list();
    }

    public void markPublished(UUID eventId) {
        jdbcClient.sql("""
                UPDATE payment.purchase_event_outbox
                SET published_at = CURRENT_TIMESTAMP, attempts = attempts + 1, last_error = NULL
                WHERE event_id = :eventId
                """).param("eventId", eventId).update();
    }

    public void markFailed(UUID eventId, String error) {
        jdbcClient.sql("""
                UPDATE payment.purchase_event_outbox
                SET attempts = attempts + 1, last_error = :error
                WHERE event_id = :eventId
                """)
                .param("eventId", eventId)
                .param("error", error.substring(0, Math.min(error.length(), 1000)))
                .update();
    }

    public record PendingEvent(UUID eventId, String payload, String eventType) {
        public PendingEvent(UUID eventId, String payload) {
            this(eventId, payload, "purchase.created");
        }
    }
}
