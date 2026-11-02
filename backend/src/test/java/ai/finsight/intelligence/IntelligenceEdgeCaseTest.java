package ai.finsight.intelligence;

import ai.finsight.budget.BudgetService;
import ai.finsight.forecast.ForecastService;
import ai.finsight.goal.FinancialGoalRepository;
import ai.finsight.health.HealthScoreService;
import ai.finsight.recurring.RecurringTransactionRepository;
import ai.finsight.transaction.TransactionRepository;
import ai.finsight.user.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.*;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class IntelligenceEdgeCaseTest {
 @Test void forecastWithNoHistoryOrSchedulesReturnsExplicitInsufficientDataAndThreeZeroMonths(){
  UserRepository users=mock(UserRepository.class);TransactionRepository transactions=mock(TransactionRepository.class);RecurringTransactionRepository recurring=mock(RecurringTransactionRepository.class);NamedParameterJdbcTemplate jdbc=mock(NamedParameterJdbcTemplate.class);when(users.findByEmailIgnoreCase("edge@example.com")).thenReturn(Optional.of(new AppUser("edge@example.com","hash","Edge")));when(transactions.sumForOwnerAndPeriod(any(),any(),any(),any())).thenReturn(BigDecimal.ZERO);when(recurring.findAllByUserIdOrderByNextDueDateAsc(any())).thenReturn(List.of());when(jdbc.queryForObject(anyString(),any(MapSqlParameterSource.class),eq(BigDecimal.class))).thenReturn(BigDecimal.ZERO);
  ForecastService.Forecast forecast=new ForecastService(users,transactions,recurring,jdbc).forecast("edge@example.com");
  assertEquals(3,forecast.months().size());assertEquals("INSUFFICIENT_DATA",forecast.confidence());assertEquals(0,forecast.months().get(0).projectedSavings().compareTo(BigDecimal.ZERO.setScale(2)));assertTrue(forecast.upcomingCommitments().isEmpty());verify(transactions,times(6)).sumForOwnerAndPeriod(any(),any(),any(),any());
 }

 @Test void healthScoreWithNoEvidenceIsNullAndAllComponentsAreUnavailable(){
  UserRepository users=mock(UserRepository.class);TransactionRepository transactions=mock(TransactionRepository.class);NamedParameterJdbcTemplate jdbc=mock(NamedParameterJdbcTemplate.class);BudgetService budgets=mock(BudgetService.class);FinancialGoalRepository goals=mock(FinancialGoalRepository.class);when(users.findByEmailIgnoreCase("empty@example.com")).thenReturn(Optional.of(new AppUser("empty@example.com","hash","Empty")));when(transactions.sumForOwnerAndPeriod(any(),any(),any(),any())).thenReturn(BigDecimal.ZERO);when(jdbc.queryForObject(anyString(),any(MapSqlParameterSource.class),eq(BigDecimal.class))).thenReturn(BigDecimal.ZERO);when(budgets.list("empty@example.com")).thenReturn(List.of());when(goals.findAllByUserIdOrderByTargetDateAsc(any())).thenReturn(List.of());
  HealthScoreService.Assessment score=new HealthScoreService(users,transactions,jdbc,budgets,goals).assess("empty@example.com");
  assertNull(score.score());assertEquals(0,score.confidenceWeight());assertEquals("INSUFFICIENT_DATA",score.confidence());assertEquals(5,score.components().size());assertTrue(score.components().stream().noneMatch(HealthScoreService.Component::available));assertFalse(score.limitations().isEmpty());
 }
}
