package pe.edu.cibertec.platonmicroservicepayments;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.cibertec.platonmicroservicepayments.dto.ProductsSoldRequest;
import pe.edu.cibertec.platonmicroservicepayments.dto.PurchaseResponse;
import pe.edu.cibertec.platonmicroservicepayments.event.PurchaseCreatedEvent;
import pe.edu.cibertec.platonmicroservicepayments.repository.ProductsSoldListRepository;
import pe.edu.cibertec.platonmicroservicepayments.repository.UserProductsRepository;
import pe.edu.cibertec.platonmicroservicepayments.repository.ProductPriceRepository;
import pe.edu.cibertec.platonmicroservicepayments.service.impl.*;
import tools.jackson.databind.json.JsonMapper;
import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.UUID;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "app.rabbitmq.purchase.publisher-enabled=false")
class PurchaseOutboxIntegrationTests {
    @Autowired private PurchaseRegistrationService registration;
    @Autowired private PaymentDecisionService decisions;
    @Autowired private ProductsSoldListRepository listing;
    @Autowired private UserProductsRepository owned;
    @Autowired private ProductPriceRepository catalogPrices;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private DataSource dataSource;
    @Autowired private JsonMapper jsonMapper;

    @Test
    void oneHeaderThreeItemsAndThreeEventsDisappearTogetherOnRollback() {
        var jdbc = JdbcClient.create(dataSource);
        Long id = new TransactionTemplate(transactionManager).execute(transaction -> {
            transaction.setRollbackOnly();
            var sale = newSale(jdbc);
            assertEquals(3, sale.products().size());
            assertEquals(new BigDecimal("72.00"), sale.totalAmount());
            assertEquals(new BigDecimal("72.00"), jdbc.sql(
                    "SELECT total_amount FROM payment.products_sold WHERE id_products_sold = :id")
                    .param("id", sale.idProductsSold()).query(BigDecimal.class).single());
            assertTrue(sale.products().stream().allMatch(item -> new BigDecimal("12.00").equals(item.unitPrice())));
            assertEquals(1L, count(jdbc, "products_sold", sale.idProductsSold()));
            assertEquals(3L, count(jdbc, "products_sold_item", sale.idProductsSold()));
            assertEvents(jdbc, sale, "purchase.created");
            return sale.idProductsSold();
        });
        assertNotNull(id);
        assertEquals(0L, count(jdbc, "products_sold", id));
        assertEquals(0L, count(jdbc, "products_sold_item", id));
        assertEquals(0L, count(jdbc, "purchase_event_outbox", id));
    }

    @Test
    void acceptingWholeSaleAssignsThreeProductsAndListingHasOneHeader() {
        var jdbc = JdbcClient.create(dataSource);
        new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
            transaction.setRollbackOnly();
            var sale = newSale(jdbc);
            jdbc.sql("UPDATE products.product SET sale_price = 999.00 WHERE idproduct IN (:ids)")
                    .param("ids", sale.products().stream().map(PurchaseResponse.Item::idProducts).toList()).update();
            var paid = decisions.accept(sale.idProductsSold(), sale.totalAmount());
            assertEquals(new BigDecimal("72.00"), paid.totalAmount());
            assertEquals("PAGADO", paid.stateSold().nameState());
            assertEquals(3, paid.products().size());
            assertEquals(paid, decisions.accept(sale.idProductsSold(), sale.totalAmount()));
            assertEquals(3L, count(jdbc, "user_products", sale.idProductsSold()));
            assertEquals(3, owned.findByIdUser(2L).stream()
                    .filter(item -> item.idProductsSold().equals(sale.idProductsSold())).count());
            // La consulta usa JDBC: flush para ver la decision de JPA dentro de esta transaccion.
            entityManager.flush();
            var page = listing.findAll(2L, "PAGADO",null, PageRequest.of(0, 100));
            var header = page.getContent().stream().filter(row -> row.idProductsSold().equals(sale.idProductsSold()))
                    .findFirst().orElseThrow();
            assertEquals(3, header.products().size());
            assertEquals(1, page.getContent().stream()
                    .filter(row -> row.idProductsSold().equals(sale.idProductsSold())).count());
        });
    }

    @Autowired private jakarta.persistence.EntityManager entityManager;

    @Test
    void rejectingWholeSaleCreatesOneReturnEventPerProductEvenWhenRepeated() {
        var jdbc = JdbcClient.create(dataSource);
        new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
            transaction.setRollbackOnly();
            var sale = newSale(jdbc);
            var rejected = decisions.reject(sale.idProductsSold());
            assertEquals("CANCELADO", rejected.stateSold().nameState());
            assertEquals(3, rejected.products().size());
            assertEquals(rejected, decisions.reject(sale.idProductsSold()));
            assertEvents(jdbc, rejected, "payment.rejected");
            assertEquals(0L, count(jdbc, "user_products", sale.idProductsSold()));
        });
    }

    private void assertEvents(JdbcClient jdbc, PurchaseResponse sale, String type) {
        var payloads = jdbc.sql("""
                SELECT payload FROM payment.purchase_event_outbox
                WHERE id_products_sold = :id AND event_type = :type
                """).param("id", sale.idProductsSold()).param("type", type).query(String.class).list();
        assertEquals(3, payloads.size());
        var events = payloads.stream().map(payload -> jsonMapper.readValue(payload, PurchaseCreatedEvent.class)).toList();
        assertEquals(3L, events.stream().map(PurchaseCreatedEvent::stockDeductionId).distinct().count());
        for (var item : sale.products()) {
            var event = events.stream().filter(value -> value.idProducts().equals(item.idProducts())).findFirst().orElseThrow();
            assertEquals(sale.idProductsSold(), event.idProductsSold());
            assertEquals(item.numberSold(), event.numberSold());
            assertEquals(type, event.eventType());
            assertNotNull(event.stockDeductionId());
        }
    }

    private long count(JdbcClient jdbc, String table, Long id) {
        return jdbc.sql("SELECT COUNT(*) FROM payment." + table + " WHERE id_products_sold = :id")
                .param("id", id).query(Long.class).single();
    }

    private PurchaseResponse newSale(JdbcClient jdbc) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        long editorial = jdbc.sql("INSERT INTO products.editorial (name) VALUES (:name) RETURNING ideditorial")
                .param("name", "Sale test editorial " + suffix).query(Long.class).single();
        long genre = jdbc.sql("INSERT INTO products.genre (name) VALUES (:name) RETURNING idgenre")
                .param("name", "Sale test genre " + suffix).query(Long.class).single();
        long state = jdbc.sql("SELECT id_state_sold FROM payment.state_sold WHERE name_state = 'PENDIENTE' LIMIT 1")
                .query(Long.class).single();
        var deductions = new ArrayList<PurchaseStockDeduction>();
        for (int i = 1; i <= 3; i++) {
            long product = jdbc.sql("""
                    INSERT INTO products.product (name, isbn, author, ideditorial, idgenre,
                        publication_year, description, purchase_price, profit_margin, sale_price)
                    VALUES (:name, :isbn, 'Test author', :editorial, :genre, 2026, 'Test fixture', 10, 20, 12)
                    RETURNING idproduct
                    """).param("name", "Sale test book " + suffix + i).param("isbn", "SALE-" + suffix + i)
                    .param("editorial", editorial).param("genre", genre).query(Long.class).single();
            var unitPrice = catalogPrices.findUnitPrices(List.of(product)).get(product);
            deductions.add(new PurchaseStockDeduction(new ProductsSoldRequest(product, 2L, i, state),
                    UUID.randomUUID(), unitPrice));
        }
        return registration.registerAll(deductions);
    }
}
