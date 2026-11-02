package ai.finsight.paypal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public record PayPalTransactionData(
        String transactionId,
        String status,
        String eventCode,
        LocalDate occurredOn,
        BigDecimal signedAmount,
        String currencyCode,
        BigDecimal feeAmount,
        String sourceFingerprint) {
    public PayPalTransactionData(String transactionId, String status, String eventCode, LocalDate occurredOn,
                                 BigDecimal signedAmount, String currencyCode, BigDecimal feeAmount) {
        this(transactionId, status, eventCode, occurredOn, signedAmount, currencyCode, feeAmount,
                fingerprint(String.join("|", nullSafe(transactionId), nullSafe(status), nullSafe(eventCode), nullSafe(occurredOn),
                        nullSafe(signedAmount), nullSafe(currencyCode), nullSafe(feeAmount))));
    }
    static String fingerprint(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static String nullSafe(Object value) { return value == null ? "" : value.toString(); }
}
