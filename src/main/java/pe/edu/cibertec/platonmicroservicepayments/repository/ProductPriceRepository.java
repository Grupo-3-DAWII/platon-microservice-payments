package pe.edu.cibertec.platonmicroservicepayments.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Consulta el precio de venta del catalogo compartido, nunca un precio enviado por el cliente. */
@Repository
public class ProductPriceRepository {
    private final JdbcClient jdbc;

    public ProductPriceRepository(DataSource dataSource) {
        jdbc = JdbcClient.create(dataSource);
    }

    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> findUnitPrices(List<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return jdbc.sql("SELECT idproduct, sale_price FROM products.product WHERE idproduct IN (:ids)")
                .param("ids", ids).query((rs, row) -> Map.entry(rs.getLong("idproduct"), rs.getBigDecimal("sale_price")))
                .list().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
