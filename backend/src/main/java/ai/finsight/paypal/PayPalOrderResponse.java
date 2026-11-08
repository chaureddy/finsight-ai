package ai.finsight.paypal;

public record PayPalOrderResponse(
        String orderId,
        String status,
        String approveUrl
) {}

