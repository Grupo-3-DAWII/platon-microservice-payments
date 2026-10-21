package pe.edu.cibertec.platonmicroservicepayments.controller;

import pe.edu.cibertec.platonmicroservicepayments.dto.ProductsSoldRequest;
import pe.edu.cibertec.platonmicroservicepayments.dto.ProductsSoldResponse;
import pe.edu.cibertec.platonmicroservicepayments.dto.ProductsSoldPageResponse;
import pe.edu.cibertec.platonmicroservicepayments.dto.ProductsSoldFilterRequest;
import pe.edu.cibertec.platonmicroservicepayments.service.ProductsSoldService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/products-sold")
@RequiredArgsConstructor
public class ProductsSoldController {

    private final ProductsSoldService service;
    //listar ventas
    //http://localhost:8082/api/v1/products-sold
    @GetMapping
    public ResponseEntity<ProductsSoldPageResponse> list(@ModelAttribute ProductsSoldFilterRequest filters) {
        return ResponseEntity.ok(service.findAll(filters));
    }

    //http://localhost:8082/api/v1/products-sold/2
    //Obtener venta por id
    @GetMapping("/{id}")
    public ProductsSoldResponse get(@PathVariable Long id) {
        return service.findById(id);
    }

    //http://localhost:8082/api/v1/products-sold
    //Registrar venta
    @PostMapping
    public ResponseEntity<ProductsSoldResponse> create(@Valid @RequestBody ProductsSoldRequest request) {
        return ResponseEntity.ok(service.create(request));
    }

    @PostMapping("/{id}/payment/accept")
    public ResponseEntity<ProductsSoldResponse> acceptPayment(@PathVariable Long id) {
        return ResponseEntity.ok(service.acceptPayment(id));
    }

    @PostMapping("/{id}/payment/reject")
    public ResponseEntity<ProductsSoldResponse> rejectPayment(@PathVariable Long id) {
        return ResponseEntity.ok(service.rejectPayment(id));
    }
    //http://localhost:8082/api/v1/products-sold/3
    //Actualizar venta
    @PutMapping("/{id}")
    public ProductsSoldResponse update(@PathVariable Long id, @Valid @RequestBody ProductsSoldRequest request) {
        return service.update(id, request);
    }

    //http://localhost:8082/api/v1/products-sold/3/state/2
    //Cambiar el estado de una venta
    @PatchMapping("/{id}/state/{idStateSold}")
    public ProductsSoldResponse changeState(@PathVariable Long id, @PathVariable Long idStateSold) {
        return service.changeState(id, idStateSold);
    }
    //http://localhost:8082/api/v1/products-sold/3
    //Eliminar venta
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
