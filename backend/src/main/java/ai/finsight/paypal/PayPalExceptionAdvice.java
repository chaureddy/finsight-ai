package ai.finsight.paypal;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(assignableTypes = PayPalController.class)
public class PayPalExceptionAdvice {
    public record SafeError(String error) {}
    @ExceptionHandler(PayPalClientException.class)
    public ResponseEntity<SafeError> paypal(PayPalClientException ex) {
        return switch (ex.getKind()) {
            case NOT_CONFIGURED -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new SafeError("PayPal Sandbox credentials are not configured on the server."));
            case REPORTING_PERMISSION -> ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new SafeError("PayPal did not grant Transaction Search reporting permission to this Sandbox app."));
            case UPSTREAM_UNAVAILABLE -> ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new SafeError("PayPal Sandbox is temporarily unavailable. Try again later."));
        };
    }
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<SafeError> status(ResponseStatusException ex) {
        String message = ex.getStatusCode().value() == 409 ? "This Sandbox reporting source is already linked to another FinSight user." : "The PayPal request could not be completed.";
        return ResponseEntity.status(ex.getStatusCode()).body(new SafeError(message));
    }
}
