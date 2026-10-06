package pe.edu.cibertec.platonmicroservicepayments.controller;

import pe.edu.cibertec.platonmicroservicepayments.dto.StateSoldRequest;
import pe.edu.cibertec.platonmicroservicepayments.dto.StateSoldResponse;
import pe.edu.cibertec.platonmicroservicepayments.service.StateSoldService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/states-sold")
@RequiredArgsConstructor
public class StateSoldController {

    private final StateSoldService service;
    //http://localhost:8082/api/v1/states-sold
    // Listar todos los estados de venta
    @GetMapping
    public List<StateSoldResponse> list() {
        return service.findAll();
    }

    //http://localhost:8082/api/v1/states-sold/2
    // Obtener un estado de venta por ID
    @GetMapping("/{id}")
    public StateSoldResponse get(@PathVariable Long id) {
        return service.findById(id);
    }

    //http://localhost:8082/api/v1/states-sold
    // Crear un nuevo estado de venta
    @PostMapping
    public ResponseEntity<StateSoldResponse> create(@Valid @RequestBody StateSoldRequest request) {
        StateSoldResponse created = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.idStateSold()).toUri();
        return ResponseEntity.created(location).body(created);
    }
    //http://localhost:8082/api/v1/states-sold/2
    // Actualizar un estado de venta existente
    @PutMapping("/{id}")
    public StateSoldResponse update(@PathVariable Long id, @Valid @RequestBody StateSoldRequest request) {
        return service.update(id, request);
    }

    //http://localhost:8082/api/v1/states-sold/2
    // Eliminar un estado de venta por ID
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
