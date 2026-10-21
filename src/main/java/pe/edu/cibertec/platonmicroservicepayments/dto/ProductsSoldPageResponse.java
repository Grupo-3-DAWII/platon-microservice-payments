package pe.edu.cibertec.platonmicroservicepayments.dto;

import java.util.List;

public record ProductsSoldPageResponse(
        List<ProductsSoldListResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        String message
) {}
