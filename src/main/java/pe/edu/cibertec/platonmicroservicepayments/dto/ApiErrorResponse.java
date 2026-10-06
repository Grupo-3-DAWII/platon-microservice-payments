package pe.edu.cibertec.platonmicroservicepayments.dto;

import java.time.LocalDateTime;
import java.util.Map;

/** Formato unico de error para toda la API. */
public record ApiErrorResponse(
        LocalDateTime timestamp,
        int status,
        String error,
        String message,
        String path,
        Map<String, String> validationErrors
) {}
