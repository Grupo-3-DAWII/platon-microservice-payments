package pe.edu.cibertec.platonmicroservicepayments.service;

import pe.edu.cibertec.platonmicroservicepayments.dto.ProductsSoldRequest;
import pe.edu.cibertec.platonmicroservicepayments.dto.ProductsSoldResponse;
import pe.edu.cibertec.platonmicroservicepayments.dto.ProductsSoldPageResponse;
import pe.edu.cibertec.platonmicroservicepayments.dto.ProductsSoldFilterRequest;
import pe.edu.cibertec.platonmicroservicepayments.dto.PurchaseRequest;
import pe.edu.cibertec.platonmicroservicepayments.dto.PurchaseResponse;
import pe.edu.cibertec.platonmicroservicepayments.dto.PaymentRequest;

public interface ProductsSoldService {

    /** Pagina las ventas filtradas por usuario y estado. */
    ProductsSoldPageResponse findAll(ProductsSoldFilterRequest filters);

    PurchaseResponse findById(Long id);

    ProductsSoldResponse create(ProductsSoldRequest request);

    PurchaseResponse createMultiple(PurchaseRequest request);

    PurchaseResponse acceptPayment(Long id, PaymentRequest request);

    PurchaseResponse rejectPayment(Long id);

    ProductsSoldResponse update(Long id, ProductsSoldRequest request);

    ProductsSoldResponse changeState(Long id, Long idStateSold);

    void delete(Long id);
}
