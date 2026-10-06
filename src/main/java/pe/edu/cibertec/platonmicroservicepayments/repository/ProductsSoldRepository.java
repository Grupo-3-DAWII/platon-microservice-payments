package pe.edu.cibertec.platonmicroservicepayments.repository;

import pe.edu.cibertec.platonmicroservicepayments.model.ProductsSold;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductsSoldRepository extends JpaRepository<ProductsSold, Long> {

    @Override
    @EntityGraph(attributePaths = "stateSold")
    List<ProductsSold> findAll();

    @EntityGraph(attributePaths = "stateSold")
    List<ProductsSold> findByIdUser(Long idUser);
}
