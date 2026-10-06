package pe.edu.cibertec.platonmicroservicepayments.config;

import pe.edu.cibertec.platonmicroservicepayments.model.StateSold;
import pe.edu.cibertec.platonmicroservicepayments.repository.StateSoldRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/** Inserta los estados iniciales si todavia no existen. */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final StateSoldRepository stateRepository;

    @Override
    public void run(String... args) {
        List.of("PENDIENTE", "PAGADO", "CANCELADO").forEach(name -> {
            if (!stateRepository.existsByNameStateIgnoreCase(name)) {
                stateRepository.save(StateSold.builder().nameState(name).build());
                log.info("Estado inicial creado: {}", name);
            }
        });
    }
}
