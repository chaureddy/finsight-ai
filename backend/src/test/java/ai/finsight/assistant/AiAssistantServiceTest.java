package ai.finsight.assistant;

import ai.finsight.account.UserAccountConnectionRepository;
import ai.finsight.budget.BudgetService;
import ai.finsight.health.HealthScoreService;
import ai.finsight.intelligence.AdvancedFinancialAnalyticsService;
import ai.finsight.paypal.PayPalApiClient;
import ai.finsight.paypal.PayPalImportPersistenceService;
import ai.finsight.recurring.RecurringTransactionRepository;
import ai.finsight.transaction.FinancialTransaction;
import ai.finsight.transaction.TransactionRepository;
import ai.finsight.user.AppUser;
import ai.finsight.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AiAssistantServiceTest {
    private UserRepository users;
    private TransactionRepository transactions;
    private RecurringTransactionRepository recurring;
    private BudgetService budgets;
    private HealthScoreService health;
    private AdvancedFinancialAnalyticsService advancedAnalytics;
    private PayPalImportPersistenceService paypalService;
    private PayPalApiClient paypalClient;
    private UserAccountConnectionRepository accountRepo;
    private NamedParameterJdbcTemplate jdbc;

    private AiAssistantService assistant;
    private final String email = "user@example.com";
    private final AppUser user = new AppUser(email, "hashed_pw", "Alex Smith");

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        transactions = mock(TransactionRepository.class);
        recurring = mock(RecurringTransactionRepository.class);
        budgets = mock(BudgetService.class);
        health = mock(HealthScoreService.class);
        advancedAnalytics = mock(AdvancedFinancialAnalyticsService.class);
        paypalService = mock(PayPalImportPersistenceService.class);
        paypalClient = mock(PayPalApiClient.class);
        accountRepo = mock(UserAccountConnectionRepository.class);
        jdbc = mock(NamedParameterJdbcTemplate.class);

        assistant = new AiAssistantService(users, transactions, recurring, budgets, health, advancedAnalytics, paypalService, paypalClient, accountRepo, jdbc);
        when(users.findByEmailIgnoreCase(email)).thenReturn(Optional.of(user));

        // Default health & analytics mocks
        when(health.assess(email)).thenReturn(new HealthScoreService.Assessment(
                new BigDecimal("82"), 85, "HIGH", List.of(), List.of(), "Informational"
        ));
        when(advancedAnalytics.analyze(email)).thenReturn(new AdvancedFinancialAnalyticsService.ComprehensiveIntelligence(
                List.of(new AdvancedFinancialAnalyticsService.MerchantSpend("Uber", new BigDecimal("1200.00"), 3, "Transportation")),
                List.of(new AdvancedFinancialAnalyticsService.DetectedRecurring("Netflix", new BigDecimal("499.00"), "Entertainment", "MONTHLY", 3, LocalDate.now())),
                List.of(),
                List.of(new AdvancedFinancialAnalyticsService.SavingsRecommendation("Trim subscriptions", "Subscriptions", "Medium", "Review unused recurring services.", new BigDecimal("500.00"))),
                new BigDecimal("499.00"),
                new BigDecimal("1200.00"),
                "Transportation"
        ));
        when(paypalService.status(any(), anyBoolean())).thenReturn(new PayPalImportPersistenceService.ConnectionStatus(
                true, true, "CONNECTED", java.time.Instant.now(), java.time.Instant.now()
        ));
        when(advancedAnalytics.analyzePayPal(any())).thenReturn(new AdvancedFinancialAnalyticsService.PayPalIntelligence(
                new BigDecimal("8500.00"), BigDecimal.ZERO, 3, new BigDecimal("246.50"),
                List.of(new AdvancedFinancialAnalyticsService.MerchantSpend("GitHub", new BigDecimal("3500.00"), 1, "Software")),
                List.of(new AdvancedFinancialAnalyticsService.DetectedRecurring("GitHub Pro", new BigDecimal("350.00"), "Software", "MONTHLY", 3, LocalDate.now())),
                List.of(new FinancialTransaction(UUID.randomUUID(), "GitHub Enterprise", "Software", new BigDecimal("3500.00"), FinancialTransaction.Direction.EXPENSE, LocalDate.now(), "PAYPAL", "TXN123", "USD"))
        ));
    }

    @Test
    void answersNetWorthQueryGroundedInUserData() {
        when(jdbc.queryForObject(contains("financial_asset"), any(org.springframework.jdbc.core.namedparam.SqlParameterSource.class), eq(BigDecimal.class)))
                .thenReturn(new BigDecimal("150000.00"));
        when(jdbc.queryForObject(contains("financial_investment"), any(org.springframework.jdbc.core.namedparam.SqlParameterSource.class), eq(BigDecimal.class)))
                .thenReturn(new BigDecimal("200000.00"));
        when(jdbc.queryForObject(contains("financial_liability"), any(org.springframework.jdbc.core.namedparam.SqlParameterSource.class), eq(BigDecimal.class)))
                .thenReturn(new BigDecimal("50000.00"));

        var response = assistant.chat(email, "What is my current net worth?");
        assertNotNull(response);
        assertTrue(response.reply().contains("Net Worth Summary"));
        assertTrue(response.reply().contains("3,00,000") || response.reply().contains("300,000"));
        assertEquals("FINSIGHT_GROUNDED_INTELLIGENCE", response.provider());
        assertFalse(response.suggestedQuestions().isEmpty());
    }

    @Test
    void answersSpendingQueryWithGroundedCategoryBreakdown() {
        when(transactions.sumForOwnerAndPeriod(any(), any(), any(), eq(FinancialTransaction.Direction.EXPENSE)))
                .thenReturn(new BigDecimal("42000.00"));
        when(transactions.categoryTotals(any(), any(), any(), eq(FinancialTransaction.Direction.EXPENSE)))
                .thenReturn(List.<Object[]>of(
                        new Object[]{"Housing", new BigDecimal("20000.00")},
                        new Object[]{"Groceries", new BigDecimal("12000.00")}
                ));

        var response = assistant.chat(email, "Where is my money going this month?");
        assertTrue(response.reply().contains("Monthly Spending Analysis"));
        assertTrue(response.reply().contains("Housing"));
        assertTrue(response.reply().contains("Groceries"));
        assertTrue(response.reply().contains("42,000"));
    }

    @Test
    void answersRecurringSubscriptionsQuery() {
        var response = assistant.chat(email, "What subscriptions am I paying for?");
        assertTrue(response.reply().contains("Recurring Payments & Subscriptions"));
        assertTrue(response.reply().contains("Netflix"));
        assertTrue(response.reply().contains("499"));
    }

    @Test
    void returnsSmartInsightsList() {
        var insights = assistant.getInsights(email);
        assertNotNull(insights);
        assertFalse(insights.isEmpty());
        assertTrue(insights.stream().anyMatch(i -> i.id().equals("health")));
        assertTrue(insights.stream().anyMatch(i -> i.id().equals("recurring")));
        assertTrue(insights.stream().anyMatch(i -> i.id().equals("paypal")));
    }

    @Test
    void answersPayPalQueryWithGroundedIntelligence() {
        var response = assistant.chat(email, "Show my PayPal activity and spending");
        assertNotNull(response);
        assertTrue(response.reply().contains("PayPal Financial Intelligence"));
        assertTrue(response.reply().contains("Connected"));
        assertTrue(response.reply().contains("GitHub Pro"));
        assertTrue(response.reply().contains("8,500") || response.reply().contains("8500"));
        assertTrue(response.groundingData().containsKey("paypalConnected"));
    }
}
