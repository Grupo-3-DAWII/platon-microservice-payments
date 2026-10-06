package pe.edu.cibertec.platonmicroservicepayments.service.impl;

import pe.edu.cibertec.platonmicroservicepayments.dto.ProductsSoldRequest;
import pe.edu.cibertec.platonmicroservicepayments.dto.ProductsSoldResponse;
import pe.edu.cibertec.platonmicroservicepayments.exception.ResourceNotFoundException;
import pe.edu.cibertec.platonmicroservicepayments.mapper.ProductsSoldMapper;
import pe.edu.cibertec.platonmicroservicepayments.model.ProductsSold;
import pe.edu.cibertec.platonmicroservicepayments.model.StateSold;
import pe.edu.cibertec.platonmicroservicepayments.repository.ProductsSoldRepository;
import pe.edu.cibertec.platonmicroservicepayments.repository.StateSoldRepository;
import pe.edu.cibertec.platonmicroservicepayments.service.ProductsSoldService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ProductsSoldServiceImpl implements ProductsSoldService {

    private final ProductsSoldRepository repository;
    private final StateSoldRepository stateRepository;
    private final ProductsSoldMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public List<ProductsSoldResponse> findAll(Long idUser) {
        List<ProductsSold> list = (idUser == null)
                ? repository.findAll()
                : repository.findByIdUser(idUser);
        return list.stream().map(mapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ProductsSoldResponse findById(Long id) {
        return mapper.toResponse(getOrThrow(id));
    }

    @Override
    public ProductsSoldResponse create(ProductsSoldRequest request) {
        StateSold state = getStateOrThrow(request.idStateSold());
        ProductsSold saved = repository.save(mapper.toEntity(request, state));
        return mapper.toResponse(saved);
    }

    @Override
    public ProductsSoldResponse update(Long id, ProductsSoldRequest request) {
        ProductsSold entity = getOrThrow(id);
        StateSold state = getStateOrThrow(request.idStateSold());
        mapper.updateEntity(request, entity, state);
        return mapper.toResponse(repository.save(entity));
    }

    @Override
    public ProductsSoldResponse changeState(Long id, Long idStateSold) {
        ProductsSold entity = getOrThrow(id);
        entity.setStateSold(getStateOrThrow(idStateSold));
        return mapper.toResponse(repository.save(entity));
    }

    @Override
    public void delete(Long id) {
        repository.delete(getOrThrow(id));
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
