package pe.edu.cibertec.platonmicroservicepayments.dto;

public record ProductsSoldFilterRequest(
        Long idUser,
        String state,
        Integer page,
        Integer size
) {}
