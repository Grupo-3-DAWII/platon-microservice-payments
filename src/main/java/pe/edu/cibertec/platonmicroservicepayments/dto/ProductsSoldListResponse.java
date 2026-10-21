package pe.edu.cibertec.platonmicroservicepayments.dto;

import java.time.LocalDateTime;

public record ProductsSoldListResponse(
        Long idProductsSold,
        String idProducts,
        Long idUser,
        Integer numberSold,
        LocalDateTime createDate,
        StateSoldResponse stateSold,
        BuyerResponse buyer,
        ProductResponse product
) {
    public record BuyerResponse(String name, String lastname) {}

    public record ProductResponse(String name, String isbn, String author) {}
}
