package pe.edu.cibertec.platonmicroservicepayments.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.cibertec.platonmicroservicepayments.dto.UserProductResponse;
import pe.edu.cibertec.platonmicroservicepayments.repository.UserProductsRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserProductsService {
    private final UserProductsRepository repository;

    @Transactional(readOnly = true)
    public List<UserProductResponse> findByIdUser(Long idUser) {
        if (idUser == null || idUser <= 0) {
            throw new IllegalArgumentException("idUser es obligatorio y debe ser mayor a 0");
        }
        return repository.findByIdUser(idUser);
    }
}
