package pe.edu.cibertec.platonmicroservicepayments.service;

import pe.edu.cibertec.platonmicroservicepayments.dto.ProductsSoldRequest;
import pe.edu.cibertec.platonmicroservicepayments.dto.ProductsSoldResponse;

import java.util.List;

public interface ProductsSoldService {

    /** Lista todas las ventas o, si idUser no es null, solo las de ese usuario. */
    List<ProductsSoldResponse> findAll(Long idUser);

    ProductsSoldResponse findById(Long id);

    ProductsSoldResponse create(ProductsSoldRequest request);

    ProductsSoldResponse update(Long id, ProductsSoldRequest request);

    ProductsSoldResponse changeState(Long id, Long idStateSold);

    void delete(Long id);
}
