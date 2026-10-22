package pe.edu.cibertec.platonmicroservicepayments;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.cibertec.platonmicroservicepayments.dto.ProductsSoldRequest;
import pe.edu.cibertec.platonmicroservicepayments.service.impl.PurchaseRegistrationService;
import pe.edu.cibertec.platonmicroservicepayments.service.impl.PurchaseStockDeduction;
import pe.edu.cibertec.platonmicroservicepayments.event.PurchaseCreatedEvent;
import tools.jackson.databind.json.JsonMapper;

import javax.sql.DataSource;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "app.rabbitmq.purchase.publisher-enabled=false")
class PurchaseOutboxIntegrationTests {

    @Autowired private PurchaseRegistrationService service;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private DataSource dataSource;
    @Autowired private JsonMapper jsonMapper;

    @Test
    void allPurchasesAndEventsShareTransactionAndDisappearOnRollback() {
        var jdbc = JdbcClient.create(dataSource);
        List<Long> purchaseIds = new TransactionTemplate(transactionManager).execute(transaction -> {
            transaction.setRollbackOnly();
            long editorial = jdbc.sql("""
                    INSERT INTO products.editorial (name) VALUES ('Outbox test editorial')
                    RETURNING ideditorial
                    """).query(Long.class).single();
            long genre = jdbc.sql("""
                    INSERT INTO products.genre (name) VALUES ('Outbox test genre') RETURNING idgenre
                    """).query(Long.class).single();
            long productId = jdbc.sql("""
                    INSERT INTO products.product (name, isbn, author, ideditorial, idgenre,
                        publication_year, description, purchase_price, profit_margin, sale_price)
                    VALUES ('Outbox test book', 'OUTBOX-TEST-BOOK', 'Test author', :editorial, :genre,
                        2026, 'Test fixture', 10, 20, 12)
                    RETURNING idproduct
                    """).param("editorial", editorial).param("genre", genre).query(Long.class).single();
            long stateId = jdbc.sql("""
                    SELECT id_state_sold FROM payment.state_sold WHERE name_state = 'PENDIENTE' LIMIT 1
                    """).query(Long.class).single();

            var purchases = service.registerAll(List.of(
                    new PurchaseStockDeduction(new ProductsSoldRequest(productId, 2L, 1, stateId), UUID.randomUUID()),
                    new PurchaseStockDeduction(new ProductsSoldRequest(productId, 2L, 2, stateId), UUID.randomUUID())));
            assertEquals(2, purchases.size());
            for (var purchase : purchases) {
                String payload = jdbc.sql("""
                        SELECT payload FROM payment.purchase_event_outbox WHERE id_products_sold = :id
                        """).param("id", purchase.idProductsSold()).query(String.class).single();
                var event = jsonMapper.readValue(payload, PurchaseCreatedEvent.class);
                assertEquals("purchase.created", event.eventType());
                assertEquals(1, event.eventVersion());
                assertEquals(purchase.idProductsSold(), event.idProductsSold());
                assertEquals(productId, event.idProducts());
                assertEquals("PENDIENTE", event.state());
                assertNotNull(event.eventId());
            }
            return purchases.stream().map(purchase -> purchase.idProductsSold()).toList();
        });

        assertNotNull(purchaseIds);
        for (Long purchaseId : purchaseIds) {
            assertEquals(0L, jdbc.sql("SELECT COUNT(*) FROM payment.products_sold WHERE id_products_sold = :id")
                    .param("id", purchaseId).query(Long.class).single());
            assertEquals(0L, jdbc.sql("SELECT COUNT(*) FROM payment.purchase_event_outbox WHERE id_products_sold = :id")
                    .param("id", purchaseId).query(Long.class).single());
        }
    }
}
