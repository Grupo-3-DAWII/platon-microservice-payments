package pe.edu.cibertec.platonmicroservicepayments.exception;

public class PurchaseValidationTimeoutException extends RuntimeException {
    public PurchaseValidationTimeoutException() {
        super("Products no respondio a la validacion de disponibilidad dentro del tiempo permitido");
    }
}
