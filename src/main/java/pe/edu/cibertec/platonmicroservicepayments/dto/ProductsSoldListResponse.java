package pe.edu.cibertec.platonmicroservicepayments.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.math.BigDecimal;

public record ProductsSoldListResponse(
        Long idProductsSold,
        Long idUser,
        LocalDateTime createDate,
        StateSoldResponse stateSold,
        BuyerResponse buyer,
        List<ItemResponse> products,
        BigDecimal totalAmount
) {
    public record ItemResponse(Long idProducts, Integer numberSold, ProductResponse product,
                               BigDecimal unitPrice, BigDecimal subtotal) {}
    public record BuyerResponse(String name, String lastname) {}

    public record ProductResponse(String name, String isbn, String author) {}
}
