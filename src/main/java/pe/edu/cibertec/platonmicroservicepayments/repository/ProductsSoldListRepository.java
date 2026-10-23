package pe.edu.cibertec.platonmicroservicepayments.repository;

import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import pe.edu.cibertec.platonmicroservicepayments.dto.ProductsSoldListResponse;
import pe.edu.cibertec.platonmicroservicepayments.dto.StateSoldResponse;
import javax.sql.DataSource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** La paginacion cuenta ventas, no sus productos. */
@Repository
public class ProductsSoldListRepository {
    private final JdbcClient jdbc;
    public ProductsSoldListRepository(DataSource dataSource) {
        jdbc = JdbcClient.create(dataSource);
    }
    public Page<ProductsSoldListResponse> findAll(Long idUser, String state, String search, Pageable pageable) {
        return query(null, idUser, state, search, pageable);
    }

    public Optional<ProductsSoldListResponse> findById(Long idProductsSold) {
        return query(idProductsSold, null, null, null, PageRequest.of(0, 1))
                .getContent().stream().findFirst();
    }

    private Page<ProductsSoldListResponse> query(Long idProductsSold, Long idUser, String state,
                                                 String search, Pageable pageable) {
        String from = """
                FROM payment.products_sold sale
                JOIN payment.state_sold state ON state.id_state_sold = sale.id_state_sold
                LEFT JOIN account.users buyer ON buyer.iduser = sale.id_user
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
        if (search != null) {
            String escaped = search.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
            parameters.put("pattern", "%" + escaped + "%");
            from += """
                     AND (
                          CONCAT_WS(' ', buyer.name, buyer.lastname) ILIKE :pattern
                          OR EXISTS (
                              SELECT 1 FROM payment.products_sold_item item
                              JOIN products.product p ON p.idproduct = item.id_products
                              WHERE item.id_products_sold = sale.id_products_sold
                                AND (p.name ILIKE :pattern OR p.isbn ILIKE :pattern OR p.author ILIKE :pattern))
                          OR EXISTS (
                              SELECT 1 FROM products.product p
                              WHERE p.idproduct = sale.id_products
                                AND (p.name ILIKE :pattern OR p.isbn ILIKE :pattern OR p.author ILIKE :pattern))
                     )
                    """;
        }
        long total = jdbc.sql("SELECT COUNT(*) " + from).params(parameters).query(Long.class).single();
        var headers = jdbc.sql("""
                SELECT sale.id_products_sold, sale.id_user, sale.create_date, sale.total_amount,
                       state.id_state_sold, state.name_state,
                       buyer.iduser AS buyer_id, buyer.name AS buyer_name, buyer.lastname
                """ + from + " ORDER BY sale.create_date DESC NULLS LAST, sale.id_products_sold DESC LIMIT :limit OFFSET :offset")
                .params(parameters).param("limit", pageable.getPageSize()).param("offset", pageable.getOffset())
                .query((rs, row) -> new ProductsSoldListResponse(
                        rs.getLong("id_products_sold"), rs.getLong("id_user"),
                        rs.getTimestamp("create_date") == null ? null : rs.getTimestamp("create_date").toLocalDateTime(),
                        new StateSoldResponse(rs.getLong("id_state_sold"), rs.getString("name_state")),
                        rs.getObject("buyer_id") == null ? null : new ProductsSoldListResponse.BuyerResponse(
                                rs.getString("buyer_name"), rs.getString("lastname")), List.of(), rs.getBigDecimal("total_amount"))).list();
        if (headers.isEmpty()) return new PageImpl<>(List.of(), pageable, total);
        var ids = headers.stream().map(ProductsSoldListResponse::idProductsSold).toList();
        var items = jdbc.sql("""
                SELECT line.id_products_sold, line.id_products, line.number_sold, line.unit_price, (line.unit_price * line.number_sold) AS subtotal,
                       product.idproduct, product.name, product.isbn, product.author
                FROM (
                    SELECT id_products_sold, id_products, number_sold, unit_price, id AS line_id
                    FROM payment.products_sold_item WHERE id_products_sold IN (:ids)
                    UNION ALL
                    SELECT sale.id_products_sold, sale.id_products, sale.number_sold, NULL::NUMERIC AS unit_price, 0 AS line_id
                    FROM payment.products_sold sale
                    WHERE sale.id_products_sold IN (:ids) AND sale.id_products IS NOT NULL
                      AND NOT EXISTS (SELECT 1 FROM payment.products_sold_item item
                                      WHERE item.id_products_sold = sale.id_products_sold)
                ) line
                LEFT JOIN products.product product ON product.idproduct = line.id_products
                ORDER BY line.id_products_sold, line.line_id
                """).param("ids", ids).query((rs, row) -> Map.entry(rs.getLong("id_products_sold"),
                new ProductsSoldListResponse.ItemResponse(rs.getLong("id_products"), rs.getInt("number_sold"),
                        rs.getObject("idproduct") == null ? null : new ProductsSoldListResponse.ProductResponse(
                                rs.getString("name"), rs.getString("isbn"), rs.getString("author")), rs.getBigDecimal("unit_price"), rs.getBigDecimal("subtotal")))).list();
        var bySale = items.stream().collect(Collectors.groupingBy(Map.Entry::getKey,
                Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
        var content = headers.stream().map(header -> new ProductsSoldListResponse(header.idProductsSold(),
                header.idUser(), header.createDate(), header.stateSold(), header.buyer(),
                bySale.getOrDefault(header.idProductsSold(), List.of()), header.totalAmount())).toList();
        return new PageImpl<>(content, pageable, total);
    }
}