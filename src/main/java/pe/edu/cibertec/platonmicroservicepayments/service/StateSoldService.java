package pe.edu.cibertec.platonmicroservicepayments.service;

import pe.edu.cibertec.platonmicroservicepayments.dto.StateSoldRequest;
import pe.edu.cibertec.platonmicroservicepayments.dto.StateSoldResponse;

import java.util.List;

public interface StateSoldService {

    List<StateSoldResponse> findAll();

    StateSoldResponse findById(Long id);

    StateSoldResponse create(StateSoldRequest request);

    StateSoldResponse update(Long id, StateSoldRequest request);

    void delete(Long id);
}
