package ai.finsight.analytics;
import java.math.BigDecimal;
import java.math.RoundingMode;
public record CashFlowSummary(BigDecimal income, BigDecimal expenses, BigDecimal netCashFlow, BigDecimal savingsRatePercent) {
 public static CashFlowSummary from(BigDecimal income, BigDecimal expenses) {
  BigDecimal safeIncome=income.setScale(2,RoundingMode.HALF_UP); BigDecimal safeExpenses=expenses.setScale(2,RoundingMode.HALF_UP);
  BigDecimal net=safeIncome.subtract(safeExpenses).setScale(2,RoundingMode.HALF_UP);
  BigDecimal rate=safeIncome.signum()<=0?BigDecimal.ZERO.setScale(2):net.multiply(BigDecimal.valueOf(100)).divide(safeIncome,2,RoundingMode.HALF_UP);
  return new CashFlowSummary(safeIncome,safeExpenses,net,rate);
 }
}
