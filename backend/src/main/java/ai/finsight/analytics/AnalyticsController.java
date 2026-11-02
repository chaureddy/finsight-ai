package ai.finsight.analytics;
import ai.finsight.transaction.FinancialTransaction;
import ai.finsight.transaction.TransactionRepository;
import ai.finsight.user.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
@RestController @RequestMapping("/api/analytics")
public class AnalyticsController {
 private final TransactionRepository transactions; private final UserRepository users;
 public AnalyticsController(TransactionRepository transactions,UserRepository users){this.transactions=transactions;this.users=users;}
 @GetMapping("/cash-flow") public CashFlowSummary cashFlow(Authentication auth){
  UUID owner=users.findByEmailIgnoreCase(auth.getName()).orElseThrow().getId(); LocalDate today=LocalDate.now(); LocalDate from=today.withDayOfMonth(1),to=from.plusMonths(1);
  BigDecimal income=transactions.sumForOwnerAndPeriod(owner,from,to,FinancialTransaction.Direction.INCOME);
  BigDecimal expenses=transactions.sumForOwnerAndPeriod(owner,from,to,FinancialTransaction.Direction.EXPENSE);
  return CashFlowSummary.from(income,expenses);
 }
}
