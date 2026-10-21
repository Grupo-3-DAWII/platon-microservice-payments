package pe.edu.cibertec.platonmicroservicepayments.repository;

import pe.edu.cibertec.platonmicroservicepayments.model.StateSold;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface StateSoldRepository extends JpaRepository<StateSold, Long> {

    Optional<StateSold> findFirstByNameStateIgnoreCase(String nameState);

    boolean existsByNameStateIgnoreCase(String nameState);

    boolean existsByNameStateIgnoreCaseAndIdStateSoldNot(String nameState, Long idStateSold);
}
