package pe.edu.cibertec.platonmicroservicepayments.service.impl;

import pe.edu.cibertec.platonmicroservicepayments.dto.StateSoldRequest;
import pe.edu.cibertec.platonmicroservicepayments.dto.StateSoldResponse;
import pe.edu.cibertec.platonmicroservicepayments.exception.DuplicateResourceException;
import pe.edu.cibertec.platonmicroservicepayments.exception.ResourceNotFoundException;
import pe.edu.cibertec.platonmicroservicepayments.mapper.StateSoldMapper;
import pe.edu.cibertec.platonmicroservicepayments.model.StateSold;
import pe.edu.cibertec.platonmicroservicepayments.repository.StateSoldRepository;
import pe.edu.cibertec.platonmicroservicepayments.service.StateSoldService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class StateSoldServiceImpl implements StateSoldService {

    private final StateSoldRepository repository;
    private final StateSoldMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public List<StateSoldResponse> findAll() {
        return repository.findAll().stream().map(mapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public StateSoldResponse findById(Long id) {
        return mapper.toResponse(getOrThrow(id));
    }

    @Override
    public StateSoldResponse create(StateSoldRequest request) {
        String name = request.nameState().trim();
        if (repository.existsByNameStateIgnoreCase(name)) {
            throw new DuplicateResourceException("Ya existe un estado con el nombre: " + name);
        }
        return mapper.toResponse(repository.save(mapper.toEntity(request)));
    }

    @Override
    public StateSoldResponse update(Long id, StateSoldRequest request) {
        StateSold entity = getOrThrow(id);
        String name = request.nameState().trim();
        if (repository.existsByNameStateIgnoreCaseAndIdStateSoldNot(name, id)) {
            throw new DuplicateResourceException("Ya existe un estado con el nombre: " + name);
        }
        mapper.updateEntity(request, entity);
        return mapper.toResponse(repository.save(entity));
    }

    @Override
    public void delete(Long id) {
        StateSold entity = getOrThrow(id);
        repository.delete(entity);
        // flush para que, si el estado esta en uso, el error se lance aqui y lo capture el handler global
        repository.flush();
    }

    private StateSold getOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Estado no encontrado con id: " + id));
    }
}
