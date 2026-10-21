package pe.edu.cibertec.platonmicroservicepayments.service;

import pe.edu.cibertec.platonmicroservicepayments.dto.ProductsSoldRequest;
import pe.edu.cibertec.platonmicroservicepayments.dto.ProductsSoldResponse;
import pe.edu.cibertec.platonmicroservicepayments.dto.ProductsSoldPageResponse;
import pe.edu.cibertec.platonmicroservicepayments.dto.ProductsSoldFilterRequest;

public interface ProductsSoldService {

    /** Pagina las ventas filtradas por usuario y estado. */
    ProductsSoldPageResponse findAll(ProductsSoldFilterRequest filters);

    ProductsSoldResponse findById(Long id);

    ProductsSoldResponse create(ProductsSoldRequest request);

    ProductsSoldResponse acceptPayment(Long id);

    ProductsSoldResponse rejectPayment(Long id);

    ProductsSoldResponse update(Long id, ProductsSoldRequest request);

    ProductsSoldResponse changeState(Long id, Long idStateSold);

    void delete(Long id);
}
