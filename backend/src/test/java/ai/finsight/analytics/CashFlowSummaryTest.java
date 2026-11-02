package ai.finsight.analytics;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.assertEquals;
class CashFlowSummaryTest {
 @Test void computesNetAndSavingsRateWithDecimalPrecision(){
  CashFlowSummary summary=CashFlowSummary.from(new BigDecimal("10000.00"),new BigDecimal("7250.25"));
  assertEquals(new BigDecimal("2749.75"),summary.netCashFlow());
  assertEquals(new BigDecimal("27.50"),summary.savingsRatePercent());
 }
 @Test void avoidsDivisionByZeroWhenNoIncome(){
  CashFlowSummary summary=CashFlowSummary.from(BigDecimal.ZERO,new BigDecimal("25.00"));
  assertEquals(new BigDecimal("-25.00"),summary.netCashFlow());
  assertEquals(new BigDecimal("0.00"),summary.savingsRatePercent());
 }
}
