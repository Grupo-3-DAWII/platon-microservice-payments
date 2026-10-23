package pe.edu.cibertec.platonmicroservicepayments.dto;

public record ProductsSoldFilterRequest(
        Long idUser,
        String state,
        String search,
        Integer page,
        Integer size
) {}
