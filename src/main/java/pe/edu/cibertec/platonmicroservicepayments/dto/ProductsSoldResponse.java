package pe.edu.cibertec.platonmicroservicepayments.dto;

import java.time.LocalDateTime;

public record ProductsSoldResponse(
        Long idProductsSold,
        Long idProducts,
        Long idUser,
        Integer numberSold,
        LocalDateTime createDate,
        StateSoldResponse stateSold
) {}
