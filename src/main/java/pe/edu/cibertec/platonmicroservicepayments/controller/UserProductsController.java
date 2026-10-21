package pe.edu.cibertec.platonmicroservicepayments.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.cibertec.platonmicroservicepayments.dto.UserProductResponse;
import pe.edu.cibertec.platonmicroservicepayments.service.UserProductsService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/user-products")
@RequiredArgsConstructor
public class UserProductsController {
    private final UserProductsService service;

    @GetMapping
    public ResponseEntity<List<UserProductResponse>> list(@RequestParam(required = false) Long idUser) {
        return ResponseEntity.ok(service.findByIdUser(idUser));
    }
}
