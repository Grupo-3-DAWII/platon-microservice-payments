package pe.edu.cibertec.platonmicroservicepayments.repository;

import pe.edu.cibertec.platonmicroservicepayments.model.ProductsSold;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductsSoldRepository extends JpaRepository<ProductsSold, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select sale from ProductsSold sale where sale.idProductsSold = :id")
    Optional<ProductsSold> findByIdForUpdate(@Param("id") Long id);

    @Override
    @EntityGraph(attributePaths = "stateSold")
    List<ProductsSold> findAll();

    @EntityGraph(attributePaths = "stateSold")
    List<ProductsSold> findByIdUser(Long idUser);
}
