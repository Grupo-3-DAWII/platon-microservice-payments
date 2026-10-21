package pe.edu.cibertec.platonmicroservicepayments.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import pe.edu.cibertec.platonmicroservicepayments.dto.ProductsSoldListResponse;
import pe.edu.cibertec.platonmicroservicepayments.dto.StateSoldResponse;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/** Lectura del listado desde los esquemas de la base de datos compartida. */
@Repository
public class ProductsSoldListRepository {

    private final JdbcClient jdbcClient;

    public ProductsSoldListRepository(DataSource dataSource) {
        this.jdbcClient = JdbcClient.create(dataSource);
    }

    public Page<ProductsSoldListResponse> findAll(Long idUser, String state, Pageable pageable) {
        String columns = """
                SELECT sale.id_products_sold, sale.id_products, sale.id_user,
                       sale.number_sold, sale.create_date,
                       state.id_state_sold, state.name_state,
                       buyer.iduser AS buyer_id, buyer.name AS buyer_name, buyer.lastname,
                       product.idproduct AS product_id, product.name AS product_name,
                       product.isbn, product.author
                """;
        String from = """
                FROM payment.products_sold sale
                JOIN payment.state_sold state ON state.id_state_sold = sale.id_state_sold
                LEFT JOIN account.users buyer ON buyer.iduser = sale.id_user
                LEFT JOIN products.product product ON product.idproduct = sale.id_products
                WHERE 1 = 1
                """;
        Map<String, Object> parameters = new HashMap<>();
        if (idUser != null) {
            from += " AND sale.id_user = :idUser";
            parameters.put("idUser", idUser);
        }
        if (state != null) {
            from += " AND UPPER(state.name_state) = :state";
            parameters.put("state", state);
        }
        long total = jdbcClient.sql("SELECT COUNT(*) " + from)
                .params(parameters).query(Long.class).single();
        var content = jdbcClient.sql(columns + from
                        + " ORDER BY sale.id_products_sold LIMIT :limit OFFSET :offset")
                .params(parameters)
                .param("limit", pageable.getPageSize())
                .param("offset", pageable.getOffset())
                .query((rs, rowNum) -> {
                    var createdAt = rs.getTimestamp("create_date");
                    var buyer = rs.getObject("buyer_id") == null ? null
                            : new ProductsSoldListResponse.BuyerResponse(
                                    rs.getString("buyer_name"), rs.getString("lastname"));
                    var product = rs.getObject("product_id") == null ? null
                            : new ProductsSoldListResponse.ProductResponse(
                                    rs.getString("product_name"), rs.getString("isbn"), rs.getString("author"));
                    return new ProductsSoldListResponse(
                            rs.getLong("id_products_sold"),
                            "0000" + rs.getLong("id_products"),
                            rs.getLong("id_user"),
                            rs.getInt("number_sold"),
                            createdAt == null ? null : createdAt.toLocalDateTime(),
                            new StateSoldResponse(rs.getLong("id_state_sold"), rs.getString("name_state")),
                            buyer, product);
                }).list();
        return new PageImpl<>(content, pageable, total);
    }
}
