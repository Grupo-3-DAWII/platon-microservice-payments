package pe.edu.cibertec.platonmicroservicepayments.repository;

import javax.sql.DataSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class OrderActorRepository {
    private final JdbcClient jdbc;
    public OrderActorRepository(DataSource dataSource) { jdbc = JdbcClient.create(dataSource); }

    public boolean canManageOrders(Long userId) {
        return jdbc.sql("""
                SELECT EXISTS (
                    SELECT 1 FROM account.users u
                    JOIN account.roles r ON r.idrole = u.idrole
                    JOIN account.status s ON s.idstatus = u.idstatus
                    WHERE u.iduser = :id AND UPPER(r.name) IN ('ADMIN', 'SELLER')
                      AND LOWER(s.name) = 'active')
                """).param("id", userId).query(Boolean.class).single();
    }
}
