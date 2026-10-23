package pe.edu.cibertec.platonmicroservicepayments.controller;

import pe.edu.cibertec.platonmicroservicepayments.dto.ProductsSoldRequest;
import pe.edu.cibertec.platonmicroservicepayments.dto.PurchaseRequest;
import pe.edu.cibertec.platonmicroservicepayments.dto.PurchaseResponse;
import pe.edu.cibertec.platonmicroservicepayments.dto.PaymentRequest;
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
@RequestMapping("/api/products-sold")
@RequiredArgsConstructor
public class ProductsSoldController {

    private final ProductsSoldService service;
    //listar ventas
    @GetMapping
    public ResponseEntity<ProductsSoldPageResponse> list(@ModelAttribute ProductsSoldFilterRequest filters) {
        return ResponseEntity.ok(service.findAll(filters));
    }

    //Obtener venta por id
    @GetMapping("/{id}")
    public PurchaseResponse get(@PathVariable Long id) {
        return service.findById(id);
    }

    //Registrar venta
    @PostMapping
    public ResponseEntity<PurchaseResponse> create(@Valid @RequestBody PurchaseRequest request) {
        return ResponseEntity.ok(service.createMultiple(request));
    }

    @PostMapping("/{id}/payment/accept")
    public ResponseEntity<PurchaseResponse> acceptPayment(@PathVariable Long id,
            @Valid @RequestBody PaymentRequest request) {
        return ResponseEntity.ok(service.acceptPayment(id, request));
    }

    @PostMapping("/{id}/payment/reject")
    public ResponseEntity<PurchaseResponse> rejectPayment(@PathVariable Long id) {
        return ResponseEntity.ok(service.rejectPayment(id));
    }
    //Actualizar venta
    @PutMapping("/{id}")
    public ProductsSoldResponse update(@PathVariable Long id, @Valid @RequestBody ProductsSoldRequest request) {
        return service.update(id, request);
    }

    //Cambiar el estado de una venta
    @PatchMapping("/{id}/state/{idStateSold}")
    public ProductsSoldResponse changeState(@PathVariable Long id, @PathVariable Long idStateSold) {
        return service.changeState(id, idStateSold);
    }
    //Eliminar venta
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
