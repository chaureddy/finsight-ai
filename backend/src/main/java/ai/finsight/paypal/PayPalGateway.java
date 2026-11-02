package ai.finsight.paypal;

import java.time.LocalDate;
import java.util.List;

public interface PayPalGateway {
    List<PayPalTransactionData> search(LocalDate startDate, LocalDate endDate);
}
