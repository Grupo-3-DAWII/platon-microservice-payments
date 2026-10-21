package pe.edu.cibertec.platonmicroservicepayments.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.cibertec.platonmicroservicepayments.model.ProductsSold;
import pe.edu.cibertec.platonmicroservicepayments.dto.UserProductResponse;

import javax.sql.DataSource;
import java.util.List;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class UserProductsRepository {
    private final JdbcClient jdbc;

    public UserProductsRepository(DataSource dataSource) {
        jdbc = JdbcClient.create(dataSource);
    }

    public void assign(ProductsSold purchase) {
        jdbc.sql("""
                INSERT INTO payment.user_products (id_products_sold, id_user, id_products, quantity)
                VALUES (:sale, :buyer, :product, :quantity)
                ON CONFLICT (id_products_sold) DO NOTHING
                """)
                .param("sale", purchase.getIdProductsSold()).param("buyer", purchase.getIdUser())
                .param("product", purchase.getIdProducts()).param("quantity", purchase.getNumberSold()).update();
    }

    public List<UserProductResponse> findByIdUser(Long idUser) {
        return jdbc.sql("""
                SELECT owned.id_products_sold, owned.id_user, owned.id_products,
                       owned.quantity, owned.acquired_at,
                       product.idproduct AS product_id, product.name, product.isbn,
                       product.author, product.image_url
                FROM payment.user_products owned
                LEFT JOIN products.product product ON product.idproduct = owned.id_products
                WHERE owned.id_user = :idUser
                ORDER BY owned.acquired_at DESC, owned.id_products_sold DESC
                """).param("idUser", idUser)
                .query((rs, rowNum) -> new UserProductResponse(
                        rs.getLong("id_products_sold"), rs.getLong("id_user"), rs.getLong("id_products"),
                        rs.getInt("quantity"), rs.getTimestamp("acquired_at").toInstant(),
                        rs.getObject("product_id") == null ? null : new UserProductResponse.ProductResponse(
                                rs.getString("name"), rs.getString("isbn"), rs.getString("author"),
                                rs.getString("image_url"))))
                .list();
    }
}
