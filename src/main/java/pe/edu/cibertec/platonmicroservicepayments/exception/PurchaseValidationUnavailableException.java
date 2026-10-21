package pe.edu.cibertec.platonmicroservicepayments.exception;

public class PurchaseValidationUnavailableException extends RuntimeException {
    public PurchaseValidationUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
