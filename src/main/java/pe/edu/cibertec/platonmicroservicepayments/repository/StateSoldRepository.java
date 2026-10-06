package pe.edu.cibertec.platonmicroservicepayments.repository;

import pe.edu.cibertec.platonmicroservicepayments.model.StateSold;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StateSoldRepository extends JpaRepository<StateSold, Long> {

    boolean existsByNameStateIgnoreCase(String nameState);

    boolean existsByNameStateIgnoreCaseAndIdStateSoldNot(String nameState, Long idStateSold);
}
