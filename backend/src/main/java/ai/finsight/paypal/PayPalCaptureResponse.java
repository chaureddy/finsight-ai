package ai.finsight.paypal;

import java.math.BigDecimal;

public record PayPalCaptureResponse(
        String orderId,
        String status,
        String captureId,
        BigDecimal amount,
        String currency,
        BigDecimal fee,
        String payerEmail
) {}

