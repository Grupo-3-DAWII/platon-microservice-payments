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
import java.util.List;
import java.math.BigDecimal;
import pe.edu.cibertec.platonmicroservicepayments.dto.PurchaseResponse;
import pe.edu.cibertec.platonmicroservicepayments.model.ProductsSold;
import pe.edu.cibertec.platonmicroservicepayments.model.ProductsSoldItem;

@Service
@RequiredArgsConstructor
public class PurchaseRegistrationService {

    private final ProductsSoldRepository repository;
    private final StateSoldRepository stateRepository;
    private final ProductsSoldMapper mapper;
    private final PurchaseOutboxRepository outbox;

    /** La cabecera, sus detalles y eventos se confirman en la misma transaccion. */
    @Transactional
    public PurchaseResponse registerAll(List<PurchaseStockDeduction> deductions) {
        if (deductions.isEmpty()) throw new IllegalArgumentException("La venta requiere productos");
        var first = deductions.getFirst().request();
        var state = stateRepository.findById(first.idStateSold())
                .orElseThrow(() -> new ResourceNotFoundException("Estado no encontrado"));
        var sale = ProductsSold.builder().idUser(first.idUser()).stateSold(state).build();
        BigDecimal total = new BigDecimal("0.00");
        for (var deduction : deductions) {
            var line = deduction.request();
            if (!first.idUser().equals(line.idUser()) || !first.idStateSold().equals(line.idStateSold())) {
                throw new IllegalArgumentException("Todos los productos deben pertenecer a la misma venta");
            }
            if (deduction.unitPrice() == null || deduction.unitPrice().signum() < 0) {
                throw new IllegalArgumentException("Cada producto requiere un precio verificado");
            }
            total = total.add(deduction.unitPrice().multiply(BigDecimal.valueOf(line.numberSold())));
            sale.getItems().add(ProductsSoldItem.builder().sale(sale).idProducts(line.idProducts())
                    .numberSold(line.numberSold()).unitPrice(deduction.unitPrice())
                    .stockDeductionId(deduction.deductionId()).build());
        }
        sale.setTotalAmount(total);
        var saved = repository.saveAndFlush(sale);
        for (var item : saved.getItems()) {
            outbox.enqueue(mapper.toItemResponse(saved, item), item.getStockDeductionId(), "purchase.created");
        }
        return mapper.toPurchaseResponse(saved);
    }

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
