package pe.edu.cibertec.platonmicroservicepayments.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.cibertec.platonmicroservicepayments.dto.ProductsSoldRequest;
import pe.edu.cibertec.platonmicroservicepayments.dto.ProductsSoldResponse;
import pe.edu.cibertec.platonmicroservicepayments.exception.ResourceNotFoundException;
import pe.edu.cibertec.platonmicroservicepayments.mapper.ProductsSoldMapper;
import pe.edu.cibertec.platonmicroservicepayments.repository.ProductsSoldRepository;
import pe.edu.cibertec.platonmicroservicepayments.repository.PurchaseOutboxRepository;
import pe.edu.cibertec.platonmicroservicepayments.repository.StateSoldRepository;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PurchaseRegistrationService {

    private final ProductsSoldRepository repository;
    private final StateSoldRepository stateRepository;
    private final ProductsSoldMapper mapper;
    private final PurchaseOutboxRepository outbox;

    @Transactional
    public ProductsSoldResponse register(ProductsSoldRequest request) {
        return register(request, null);
    }

    @Transactional
    public ProductsSoldResponse register(ProductsSoldRequest request, UUID deductionId) {
        var state = stateRepository.findById(request.idStateSold())
                .orElseThrow(() -> new ResourceNotFoundException("Estado no encontrado con id: " + request.idStateSold()));
        var entity = mapper.toEntity(request, state);
        entity.setStockDeductionId(deductionId);
        var saved = repository.save(entity);
        var response = mapper.toResponse(saved);
        outbox.enqueue(response, deductionId, "purchase.created");
        return response;
    }
}
