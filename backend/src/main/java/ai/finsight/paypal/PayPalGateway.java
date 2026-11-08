package ai.finsight.paypal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface PayPalGateway {
    List<PayPalTransactionData> search(LocalDate startDate, LocalDate endDate);
    PayPalOrderResponse createOrder(BigDecimal amount, String currency, String description);
    PayPalCaptureResponse captureOrder(String orderId);
}
