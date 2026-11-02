package ai.finsight.paypal;

public class PayPalClientException extends RuntimeException {
    private final Kind kind;
    public enum Kind { NOT_CONFIGURED, REPORTING_PERMISSION, UPSTREAM_UNAVAILABLE }
    public PayPalClientException(Kind kind) { super("PayPal Sandbox request failed"); this.kind = kind; }
    public Kind getKind() { return kind; }
}
