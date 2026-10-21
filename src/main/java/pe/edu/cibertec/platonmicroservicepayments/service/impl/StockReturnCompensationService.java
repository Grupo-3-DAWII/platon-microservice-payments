package pe.edu.cibertec.platonmicroservicepayments.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.cibertec.platonmicroservicepayments.dto.ProductsSoldRequest;
import pe.edu.cibertec.platonmicroservicepayments.dto.ProductsSoldResponse;
import pe.edu.cibertec.platonmicroservicepayments.dto.StateSoldResponse;
import pe.edu.cibertec.platonmicroservicepayments.repository.PurchaseOutboxRepository;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StockReturnCompensationService {
    private final PurchaseOutboxRepository outbox;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void enqueue(ProductsSoldRequest request, UUID deductionId) {
        var failed = new ProductsSoldResponse(null, request.idProducts(), request.idUser(), request.numberSold(),
                LocalDateTime.now(), new StateSoldResponse(null, "CANCELADO"));
        outbox.enqueue(failed, deductionId, "payment.rejected");
    }
}
