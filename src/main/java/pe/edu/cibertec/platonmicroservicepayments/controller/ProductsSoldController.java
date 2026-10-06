package pe.edu.cibertec.platonmicroservicepayments.controller;

import pe.edu.cibertec.platonmicroservicepayments.dto.ProductsSoldRequest;
import pe.edu.cibertec.platonmicroservicepayments.dto.ProductsSoldResponse;
import pe.edu.cibertec.platonmicroservicepayments.service.ProductsSoldService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/products-sold")
@RequiredArgsConstructor
public class ProductsSoldController {

    private final ProductsSoldService service;

    @GetMapping
    public List<ProductsSoldResponse> list(@RequestParam(required = false) Long idUser) {
        return service.findAll(idUser);
    }

    @GetMapping("/{id}")
    public ProductsSoldResponse get(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    public ResponseEntity<ProductsSoldResponse> create(@Valid @RequestBody ProductsSoldRequest request) {
        ProductsSoldResponse created = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.idProductsSold()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    public ProductsSoldResponse update(@PathVariable Long id, @Valid @RequestBody ProductsSoldRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/{id}/state/{idStateSold}")
    public ProductsSoldResponse changeState(@PathVariable Long id, @PathVariable Long idStateSold) {
        return service.changeState(id, idStateSold);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
