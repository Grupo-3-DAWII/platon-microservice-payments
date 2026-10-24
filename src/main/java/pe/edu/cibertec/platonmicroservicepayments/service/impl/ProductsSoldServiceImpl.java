package pe.edu.cibertec.platonmicroservicepayments.service.impl;

import pe.edu.cibertec.platonmicroservicepayments.dto.*;
import pe.edu.cibertec.platonmicroservicepayments.repository.ProductPriceRepository;
import java.math.BigDecimal;
import pe.edu.cibertec.platonmicroservicepayments.exception.ResourceNotFoundException;
import pe.edu.cibertec.platonmicroservicepayments.exception.ProductUnavailableException;
import pe.edu.cibertec.platonmicroservicepayments.mapper.ProductsSoldMapper;
import pe.edu.cibertec.platonmicroservicepayments.model.ProductsSold;
import pe.edu.cibertec.platonmicroservicepayments.model.StateSold;
import pe.edu.cibertec.platonmicroservicepayments.repository.ProductsSoldRepository;
import pe.edu.cibertec.platonmicroservicepayments.repository.ProductsSoldListRepository;
import pe.edu.cibertec.platonmicroservicepayments.repository.StateSoldRepository;
import pe.edu.cibertec.platonmicroservicepayments.event.PurchaseAvailabilityClient;
import pe.edu.cibertec.platonmicroservicepayments.service.ProductsSoldService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.data.domain.PageRequest;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.ArrayList;
import java.util.HashSet;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class ProductsSoldServiceImpl implements ProductsSoldService {

    private final ProductsSoldRepository repository;
    private final ProductsSoldListRepository listRepository;
    private final StateSoldRepository stateRepository;
    private final ProductsSoldMapper mapper;
    private final PurchaseAvailabilityClient availabilityClient;
    private final PurchaseRegistrationService purchaseRegistration;
    private final PaymentDecisionService paymentDecision;
    private final StockReturnCompensationService compensation;
    private final ProductPriceRepository prices;

    @Override
    @Transactional(readOnly = true)
    public ProductsSoldPageResponse findAll(ProductsSoldFilterRequest filters) {
        int page = filters.page() == null ? 0 : filters.page();
        int size = filters.size() == null ? 10 : filters.size();
        String state = filters.state();
        if (page < 0) {
            throw new IllegalArgumentException("page debe ser mayor o igual a 0");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("size debe estar entre 1 y 100");
        }
        String normalizedState = state == null || state.isBlank()
                ? null : state.trim().toUpperCase(Locale.ROOT);
        if (normalizedState != null && !Set.of("PENDIENTE", "PAGADO", "CANCELADO").contains(normalizedState)) {
            throw new IllegalArgumentException("state debe ser PENDIENTE, PAGADO o CANCELADO");
        }
        String search = filters.search() == null || filters.search().isBlank()
                ? null : filters.search().trim();
        if (search != null && search.length() > 100) {
            throw new IllegalArgumentException("search no puede superar 100 caracteres");
        }

        var sales = listRepository.findAll(filters.idUser(), normalizedState, search,
                PageRequest.of(page, size));

        String message = null;
        if (sales.isEmpty()) {
            if (sales.getTotalElements() > 0) {
                message = "No hay pedidos en esta pagina";
            } else if (search != null) {
                message = "No se encontraron pedidos para la búsqueda";
            } else {
                message = switch (normalizedState == null ? "TODOS" : normalizedState) {
                    case "PAGADO" -> "No hay pedidos pagados";
                    case "CANCELADO" -> "No hay pedidos cancelados";
                    case "PENDIENTE" -> "No hay pedidos pendientes";
                    default -> "No hay pedidos";
                };
            }
        }
        return new ProductsSoldPageResponse(sales.getContent(), page, size,
                sales.getTotalElements(), sales.getTotalPages(), message);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductsSoldListResponse findById(Long id) {
        return listRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Venta no encontrada con id: " + id));
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ProductsSoldResponse create(ProductsSoldRequest request) {
        if (!"PENDIENTE".equalsIgnoreCase(getStateOrThrow(request.idStateSold()).getNameState())) {
            throw new IllegalArgumentException("La solicitud de compra debe iniciar en estado PENDIENTE");
        }
        UUID deductionId = UUID.randomUUID();
        try {
            availabilityClient.requireAvailable(request, deductionId);
            return purchaseRegistration.register(request, deductionId);
        } catch (RuntimeException ex) {
            try {
                compensation.enqueue(request, deductionId);
            } catch (RuntimeException compensationFailure) {
                log.error("No se pudo registrar la devolucion compensatoria {}", deductionId, compensationFailure);
                ex.addSuppressed(compensationFailure);
            }
            throw ex;
        }
    }

    @Override
    public PurchaseResponse acceptPayment(Long id, PaymentRequest request) {
        return paymentDecision.accept(id, request.amount());
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PurchaseResponse createMultiple(PurchaseRequest request) {
        if (!"PENDIENTE".equalsIgnoreCase(getStateOrThrow(request.idStateSold()).getNameState())) {
            throw new IllegalArgumentException("La solicitud de compra debe iniciar en estado PENDIENTE");
        }
        var productIds = new HashSet<Long>();
        for (var item : request.products()) {
            if (!productIds.add(item.idProducts())) {
                throw new IllegalArgumentException("No repita productos; indique la cantidad total en numberSold");
            }
        }
        var unitPrices = prices.findUnitPrices(request.products().stream()
                .map(PurchaseRequest.Item::idProducts).toList());
        BigDecimal total = new BigDecimal("0.00");
        for (var item : request.products()) {
            var price = unitPrices.get(item.idProducts());
            if (price == null || price.signum() < 0) {
                throw new ProductUnavailableException("No hay un precio valido para el producto " + item.idProducts());
            }
            total = total.add(price.multiply(BigDecimal.valueOf(item.numberSold())));
        }
        if (request.totalAmount() == null || total.compareTo(request.totalAmount()) != 0) {
            throw new ProductUnavailableException("El monto enviado no coincide con el total calculado: " + total);
        }
        var attempted = new ArrayList<PurchaseStockDeduction>();
        try {
            for (var item : request.products()) {
                var line = new ProductsSoldRequest(item.idProducts(), request.idUser(),
                        item.numberSold(), request.idStateSold());
                var deduction = new PurchaseStockDeduction(line, UUID.randomUUID(), unitPrices.get(item.idProducts()));
                // Incluir tambien la solicitud que falla: un timeout puede ocultar un descuento aplicado.
                attempted.add(deduction);
                availabilityClient.requireAvailable(line, deduction.deductionId());
            }
            return purchaseRegistration.registerAll(attempted);
        } catch (RuntimeException ex) {
            for (var deduction : attempted) {
                try {
                    compensation.enqueue(deduction.request(), deduction.deductionId());
                } catch (RuntimeException compensationFailure) {
                    log.error("No se pudo registrar la devolucion compensatoria {}",
                            deduction.deductionId(), compensationFailure);
                    ex.addSuppressed(compensationFailure);
                }
            }
            throw ex;
        }
    }

    @Override
    public PurchaseResponse rejectPayment(Long id) {
        return paymentDecision.reject(id);
    }

    @Override
    public ProductsSoldResponse update(Long id, ProductsSoldRequest request) {
        ProductsSold entity = getOrThrow(id);
        if (entity.hasStockDeduction()) {
            throw new ProductUnavailableException("Use los endpoints de aceptar o rechazar el pago para esta compra");
        }
        StateSold state = getStateOrThrow(request.idStateSold());
        mapper.updateEntity(request, entity, state);
        return mapper.toResponse(repository.save(entity));
    }

    @Override
    public ProductsSoldResponse changeState(Long id, Long idStateSold) {
        ProductsSold entity = getOrThrow(id);
        if (entity.hasStockDeduction()) {
            throw new ProductUnavailableException("Use los endpoints de aceptar o rechazar el pago para esta compra");
        }
        entity.setStateSold(getStateOrThrow(idStateSold));
        return mapper.toResponse(repository.save(entity));
    }

    @Override
    public void delete(Long id) {
        var purchase = getOrThrow(id);
        if (purchase.hasStockDeduction()) {
            throw new ProductUnavailableException("Las compras con stock descontado se conservan; rechace el pago para devolverlo");
        }
        repository.delete(purchase);
    }

    private ProductsSold getOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Venta no encontrada con id: " + id));
    }

    private StateSold getStateOrThrow(Long idStateSold) {
        return stateRepository.findById(idStateSold)
                .orElseThrow(() -> new ResourceNotFoundException("Estado no encontrado con id: " + idStateSold));
    }
}
