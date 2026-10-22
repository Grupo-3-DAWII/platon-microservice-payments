package pe.edu.cibertec.platonmicroservicepayments.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.math.BigDecimal;

public record PurchaseResponse(Long idProductsSold, Long idUser, LocalDateTime createDate,
                               StateSoldResponse stateSold, List<Item> products, BigDecimal totalAmount) {
    public record Item(Long idProducts, Integer numberSold, BigDecimal unitPrice, BigDecimal subtotal) {}
}
