package pe.edu.cibertec.platonmicroservicepayments.dto;

import java.time.Instant;

public record UserProductResponse(
        Long idProductsSold,
        Long idUser,
        Long idProducts,
        Integer quantity,
        Instant acquiredAt,
        ProductResponse product
) {
    public record ProductResponse(String name, String isbn, String author, String imageUrl) {}
}
