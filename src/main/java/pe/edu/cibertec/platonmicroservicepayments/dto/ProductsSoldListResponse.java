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
        BigDecimal totalAmount,
        String paymentMethod,
        LocalDateTime paidAt,
        String paymentReference,
        String orderStatus
) {
    public ProductsSoldListResponse(Long id, Long user, LocalDateTime date, StateSoldResponse state,
            BuyerResponse buyer, List<ItemResponse> items, BigDecimal total,
            String method, LocalDateTime paid, String reference) {
        this(id, user, date, state, buyer, items, total, method, paid, reference, "PENDIENTE");
    }
    public ProductsSoldListResponse(Long id, Long user, LocalDateTime date, StateSoldResponse state,
            BuyerResponse buyer, List<ItemResponse> items, BigDecimal total) {
        this(id, user, date, state, buyer, items, total, null, null, null);
    }
    public record ItemResponse(Long idProducts, Integer numberSold, ProductResponse product,
                               BigDecimal unitPrice, BigDecimal subtotal) {}
    public record BuyerResponse(String name, String lastname) {}

    public record ProductResponse(String name, String isbn, String author) {}
}
