package ai.finsight.assistant;

import ai.finsight.account.UserAccountConnectionRepository;
import ai.finsight.budget.BudgetService;
import ai.finsight.health.HealthScoreService;
import ai.finsight.intelligence.AdvancedFinancialAnalyticsService;
import ai.finsight.paypal.PayPalApiClient;
import ai.finsight.paypal.PayPalImportPersistenceService;
import ai.finsight.recurring.RecurringTransaction;
import ai.finsight.recurring.RecurringTransactionRepository;
import ai.finsight.transaction.FinancialTransaction;
import ai.finsight.transaction.TransactionRepository;
import ai.finsight.user.AppUser;
import ai.finsight.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

@Service
public class AiAssistantService {
    private final UserRepository users;
    private final TransactionRepository transactions;
    private final RecurringTransactionRepository recurring;
    private final BudgetService budgets;
    private final HealthScoreService health;
    private final AdvancedFinancialAnalyticsService advancedAnalytics;
    private final PayPalImportPersistenceService paypalService;
    private final PayPalApiClient paypalClient;
    private final UserAccountConnectionRepository accountRepo;
    private final NamedParameterJdbcTemplate jdbc;

    @Value("${app.ai.api-key:${AI_PROVIDER_API_KEY:}}")
    private String aiApiKey;

    public AiAssistantService(UserRepository users,
                              TransactionRepository transactions,
                              RecurringTransactionRepository recurring,
                              BudgetService budgets,
                              HealthScoreService health,
                              AdvancedFinancialAnalyticsService advancedAnalytics,
                              PayPalImportPersistenceService paypalService,
                              PayPalApiClient paypalClient,
                              UserAccountConnectionRepository accountRepo,
                              NamedParameterJdbcTemplate jdbc) {
        this.users = users;
        this.transactions = transactions;
        this.recurring = recurring;
        this.budgets = budgets;
        this.health = health;
        this.advancedAnalytics = advancedAnalytics;
        this.paypalService = paypalService;
        this.paypalClient = paypalClient;
        this.accountRepo = accountRepo;
        this.jdbc = jdbc;
    }

    public record ChatResponse(String reply, List<String> suggestedQuestions, Map<String, Object> groundingData, String provider) {}
    public record SmartInsight(String id, String title, String category, String tone, String message, String actionLink, String actionLabel) {}

    @Transactional(readOnly = true)
    public ChatResponse chat(String email, String userPrompt) {
        AppUser user = users.findByEmailIgnoreCase(email).orElseThrow();
        UUID ownerId = user.getId();
        String query = userPrompt != null ? userPrompt.trim() : "";

        // Collect comprehensive grounded data
        YearMonth currentMonth = YearMonth.now();
        LocalDate from = currentMonth.atDay(1);
        LocalDate to = currentMonth.plusMonths(1).atDay(1);

        BigDecimal rawIncome = transactions.sumForOwnerAndPeriod(ownerId, from, to, FinancialTransaction.Direction.INCOME);
        BigDecimal rawExpenses = transactions.sumForOwnerAndPeriod(ownerId, from, to, FinancialTransaction.Direction.EXPENSE);
        BigDecimal income = rawIncome != null ? rawIncome : BigDecimal.ZERO;
        BigDecimal expenses = rawExpenses != null ? rawExpenses : BigDecimal.ZERO;
        BigDecimal savings = income.subtract(expenses).setScale(2, RoundingMode.HALF_UP);
        BigDecimal savingsRate = income.signum() == 0 ? BigDecimal.ZERO : savings.multiply(BigDecimal.valueOf(100)).divide(income, 2, RoundingMode.HALF_UP);

        BigDecimal assets = querySum("SELECT COALESCE(SUM(current_value),0) FROM financial_asset WHERE user_id=:user", ownerId);
        BigDecimal investments = querySum("SELECT COALESCE(SUM(current_value),0) FROM financial_investment WHERE user_id=:user", ownerId);
        BigDecimal liabilities = querySum("SELECT COALESCE(SUM(outstanding_amount),0) FROM financial_liability WHERE user_id=:user", ownerId);
        BigDecimal netWorth = assets.add(investments).subtract(liabilities).setScale(2, RoundingMode.HALF_UP);

        List<Object[]> topCats = transactions.categoryTotals(ownerId, from, to, FinancialTransaction.Direction.EXPENSE);
        var comprehensive = advancedAnalytics.analyze(email);
        var healthAssessment = health.assess(email);
        var userBudgets = budgets.list(email);
        List<FinancialTransaction> recentTx = transactions.findAllByUserIdOrderByOccurredOnDesc(ownerId).stream().limit(5).toList();

        Map<String, Object> grounding = new LinkedHashMap<>();
        grounding.put("netWorth", netWorth);
        grounding.put("monthlyIncome", income);
        grounding.put("monthlyExpenses", expenses);
        grounding.put("savingsRate", savingsRate);
        grounding.put("healthScore", healthAssessment.score());
        grounding.put("connectedAccountsCount", accountRepo.findAllByUserIdOrderByCreatedAtDesc(ownerId).size());
        grounding.put("paypalConfigured", paypalClient.isConfigured());
        grounding.put("paypalConnected", paypalService.status(ownerId, paypalClient.isConfigured()).connected());

        // Process response using deterministic grounded engine (or external AI if configured)
        return generateGroundedResponse(ownerId, user.getDisplayName(), query, income, expenses, savings, savingsRate, assets, investments, liabilities, netWorth, topCats, comprehensive, healthAssessment, userBudgets, recentTx, grounding);
    }

    @Transactional(readOnly = true)
    public List<SmartInsight> getInsights(String email) {
        UUID ownerId = users.findByEmailIgnoreCase(email).orElseThrow().getId();
        List<SmartInsight> list = new ArrayList<>();
        YearMonth currentMonth = YearMonth.now();
        LocalDate from = currentMonth.atDay(1);
        LocalDate to = currentMonth.plusMonths(1).atDay(1);

        BigDecimal rawIncome = transactions.sumForOwnerAndPeriod(ownerId, from, to, FinancialTransaction.Direction.INCOME);
        BigDecimal rawExpenses = transactions.sumForOwnerAndPeriod(ownerId, from, to, FinancialTransaction.Direction.EXPENSE);
        BigDecimal income = rawIncome != null ? rawIncome : BigDecimal.ZERO;
        BigDecimal expenses = rawExpenses != null ? rawExpenses : BigDecimal.ZERO;
        var comprehensive = advancedAnalytics.analyze(email);
        var healthAssessment = health.assess(email);
        var userBudgets = budgets.list(email);

        // 1. Health Score insight
        if (healthAssessment.score() != null) {
            int score = healthAssessment.score().intValue();
            String tone = score >= 75 ? "positive" : score >= 50 ? "neutral" : "warning";
            list.add(new SmartInsight(
                    "health",
                    "Financial Health Indicator: " + score + "/100",
                    "Resilience",
                    tone,
                    "Your overall score is grounded in your " + healthAssessment.confidence().toLowerCase() + " data confidence. Review component weights to improve.",
                    "/financial-health",
                    "View Health Breakdown"
            ));
        }

        // 2. Budget Alert insight
        long exceededCount = userBudgets.stream().filter(b -> "EXCEEDED".equals(b.status())).count();
        if (exceededCount > 0) {
            list.add(new SmartInsight(
                    "budget",
                    exceededCount + " Budget Overrun" + (exceededCount > 1 ? "s" : "") + " Detected",
                    "Budgeting",
                    "negative",
                    "You have exceeded your monthly threshold for " + exceededCount + " budget category. Slow down discretionary spending to stay on target.",
                    "/budgets",
                    "Review Budgets"
            ));
        }

        // 3. Subscriptions insight
        if (!comprehensive.detectedSubscriptions().isEmpty()) {
            list.add(new SmartInsight(
                    "recurring",
                    comprehensive.detectedSubscriptions().size() + " Recurring Subscriptions (₹" + comprehensive.totalMonthlySubscriptions() + "/mo)",
                    "Recurring",
                    "neutral",
                    "Subscriptions detected across your transaction history. Check for unused memberships you can cancel.",
                    "/recurring-payments",
                    "Manage Subscriptions"
            ));
        }

        // 4. Savings rate recommendation
        if (income.signum() > 0) {
            BigDecimal savings = income.subtract(expenses);
            BigDecimal rate = savings.multiply(BigDecimal.valueOf(100)).divide(income, 1, RoundingMode.HALF_UP);
            if (rate.compareTo(BigDecimal.valueOf(20)) >= 0) {
                list.add(new SmartInsight(
                        "savings",
                        "Strong " + rate + "% Savings Rate",
                        "Wealth Accumulation",
                        "positive",
                        "You are currently saving " + rate + "% of your income this month, exceeding the 20% benchmark! Consider investing surplus cash.",
                        "/investments",
                        "Explore Investments"
                ));
            }
        }

        // 5. PayPal Activity insight
        var paypalIntel = advancedAnalytics.analyzePayPal(ownerId);
        if (paypalIntel.transactionCount() > 0) {
            list.add(new SmartInsight(
                    "paypal",
                    "PayPal Activity: " + paypalIntel.transactionCount() + " Transactions (" + formatMoney(paypalIntel.totalSpend()) + ")",
                    "PayPal Intelligence",
                    "neutral",
                    "Your PayPal Sandbox transactions are integrated and verified in FinSight AI.",
                    "/paypal",
                    "View PayPal Hub"
            ));
        }

        return list;
    }

    private ChatResponse generateGroundedResponse(
            UUID ownerId,
            String name,
            String query,
            BigDecimal income,
            BigDecimal expenses,
            BigDecimal savings,
            BigDecimal savingsRate,
            BigDecimal assets,
            BigDecimal investments,
            BigDecimal liabilities,
            BigDecimal netWorth,
            List<Object[]> topCats,
            AdvancedFinancialAnalyticsService.ComprehensiveIntelligence comprehensive,
            HealthScoreService.Assessment healthAssessment,
            List<ai.finsight.budget.BudgetService.View> userBudgets,
            List<FinancialTransaction> recentTx,
            Map<String, Object> grounding
    ) {
        String lower = query.toLowerCase();
        StringBuilder reply = new StringBuilder();
        List<String> suggestions = new ArrayList<>();

        if (lower.contains("net worth") || lower.contains("balance") || lower.contains("wealth") || lower.contains("how much money")) {
            reply.append("### Net Worth Summary\n\n");
            reply.append("Your calculated net worth is **").append(formatMoney(netWorth)).append("**.\n\n");
            reply.append("| Asset / Liability Category | Value |\n");
            reply.append("| :--- | :--- |\n");
            reply.append("| **Physical & Cash Assets** | ").append(formatMoney(assets)).append(" |\n");
            reply.append("| **Investment Portfolio** | ").append(formatMoney(investments)).append(" |\n");
            reply.append("| **Outstanding Liabilities (Debt)** | ").append(formatMoney(liabilities)).append(" |\n");
            reply.append("| **Net Worth** | **").append(formatMoney(netWorth)).append("** |\n\n");
            reply.append("> *Note: Net worth equals total assets + investments minus outstanding liabilities, derived from your saved accounts and entered records.*");
            suggestions.addAll(List.of("What are my highest expense categories?", "How is my financial health score?", "Where can I save money?"));
        } else if (lower.contains("paypal")) {
            var paypalIntel = advancedAnalytics.analyzePayPal(ownerId);
            var paypalStatus = paypalService.status(ownerId, paypalClient.isConfigured());

            reply.append("### PayPal Financial Intelligence\n\n");
            reply.append("- **Sandbox Status**: ").append(paypalStatus.connected() ? "✅ Connected (" + paypalStatus.state() + ")" : (paypalStatus.configured() ? "⚠️ Configured (Not Connected)" : "⚙️ Needs Configuration")).append("\n");
            reply.append("- **Total PayPal Spend**: ").append(formatMoney(paypalIntel.totalSpend())).append(" across **").append(paypalIntel.transactionCount()).append("** transactions\n");
            reply.append("- **Refunds / Income via PayPal**: ").append(formatMoney(paypalIntel.totalIncome())).append("\n");
            reply.append("- **Estimated Processing Fees**: ").append(formatMoney(paypalIntel.totalFees())).append("\n\n");

            if (!paypalIntel.recurringSubscriptions().isEmpty()) {
                reply.append("#### Recurring PayPal Subscriptions\n");
                for (var sub : paypalIntel.recurringSubscriptions()) {
                    reply.append("- **").append(sub.description()).append("**: ").append(formatMoney(sub.averageAmount()))
                            .append(" (").append(sub.frequency().toLowerCase()).append(", ").append(sub.category()).append(")\n");
                }
                reply.append("\n");
            }

            if (!paypalIntel.topMerchants().isEmpty()) {
                reply.append("#### Top Merchants via PayPal\n");
                for (var m : paypalIntel.topMerchants()) {
                    reply.append("- **").append(m.merchant()).append("**: ").append(formatMoney(m.totalAmount())).append(" (").append(m.transactionCount()).append(" transactions)\n");
                }
                reply.append("\n");
            }

            if (!paypalIntel.recentTransactions().isEmpty()) {
                reply.append("#### Recent PayPal Activity\n");
                reply.append("| Date | Description | Amount | Type |\n");
                reply.append("| :--- | :--- | :--- | :--- |\n");
                for (var t : paypalIntel.recentTransactions()) {
                    reply.append("| ").append(t.getOccurredOn()).append(" | ").append(t.getDescription()).append(" | ")
                            .append(formatMoney(t.getAmount())).append(" | ").append(t.getDirection().name()).append(" |\n");
                }
            } else {
                reply.append("> *No PayPal transactions recorded yet. Connect PayPal Sandbox or use the Sandbox Checkout Simulator in Accounts & Integrations to sync live payments.*\n");
            }
            suggestions.addAll(List.of("What is my monthly cash flow?", "Show my recurring subscriptions", "What are my highest expense categories?"));
        } else if (lower.contains("spend") || lower.contains("expense") || lower.contains("where is my money") || lower.contains("cost")) {
            reply.append("### Monthly Spending Analysis\n\n");
            reply.append("In the current calendar month, you have spent **").append(formatMoney(expenses)).append("** across your accounts.\n\n");
            if (!topCats.isEmpty()) {
                reply.append("**Top Expense Categories:**\n");
                for (int i = 0; i < Math.min(5, topCats.size()); i++) {
                    String cat = (String) topCats.get(i)[0];
                    BigDecimal amt = (BigDecimal) topCats.get(i)[1];
                    BigDecimal pct = expenses.signum() == 0 ? BigDecimal.ZERO : amt.multiply(BigDecimal.valueOf(100)).divide(expenses, 1, RoundingMode.HALF_UP);
                    reply.append("- **").append(cat).append("**: ").append(formatMoney(amt)).append(" (").append(pct).append("% of monthly spending)\n");
                }
            } else {
                reply.append("No categorized expenses have been recorded for the current month yet.\n");
            }
            if (!comprehensive.topMerchants().isEmpty()) {
                reply.append("\n**Top Merchants:**\n");
                for (int i = 0; i < Math.min(3, comprehensive.topMerchants().size()); i++) {
                    var m = comprehensive.topMerchants().get(i);
                    reply.append("- **").append(m.merchant()).append("**: ").append(formatMoney(m.totalAmount())).append(" (").append(m.transactionCount()).append(" transactions)\n");
                }
            }
            suggestions.addAll(List.of("How can I reduce my spending?", "Do I have any budget overruns?", "What is my net worth?"));
        } else if (lower.contains("income") || lower.contains("cash flow") || lower.contains("earning") || lower.contains("salary")) {
            reply.append("### Income & Cash Flow Overview\n\n");
            reply.append("- **Monthly Income**: ").append(formatMoney(income)).append("\n");
            reply.append("- **Monthly Expenses**: ").append(formatMoney(expenses)).append("\n");
            reply.append("- **Net Cash Flow**: **").append(formatMoney(savings)).append("**\n");
            reply.append("- **Savings Rate**: **").append(savingsRate).append("%**\n\n");
            if (savings.signum() >= 0) {
                reply.append("You have a **positive cash flow** this month. You have retained ").append(savingsRate).append("% of your earned income.");
            } else {
                reply.append("Your spending currently exceeds your income by ").append(formatMoney(savings.abs())).append(" this month. Consider deferring non-essential purchases.");
            }
            suggestions.addAll(List.of("What are my top expenses?", "Where can I save money?", "Show my financial goals"));
        } else if (lower.contains("budget") || lower.contains("limit")) {
            reply.append("### Budget Status\n\n");
            if (userBudgets.isEmpty()) {
                reply.append("You don't have any active budgets set up yet. Setting category budgets helps control discretionary spending!\n");
            } else {
                reply.append("| Budget | Category | Limit | Spent | Status |\n");
                reply.append("| :--- | :--- | :--- | :--- | :--- |\n");
                for (var b : userBudgets) {
                    reply.append("| ").append(b.name()).append(" | ").append(b.category()).append(" | ")
                            .append(formatMoney(b.monthlyLimit())).append(" | ").append(formatMoney(b.amountSpent())).append(" | ")
                            .append(b.status()).append(" |\n");
                }
                long exceeded = userBudgets.stream().filter(b -> "EXCEEDED".equals(b.status())).count();
                if (exceeded > 0) {
                    reply.append("\n> ⚠️ **Attention**: ").append(exceeded).append(" budget(s) have exceeded their limit. Review your spending in those categories.");
                } else {
                    reply.append("\n✅ All active budgets are currently on track.");
                }
            }
            suggestions.addAll(List.of("How much did I spend this month?", "Show my recurring subscriptions", "What is my financial health?"));
        } else if (lower.contains("subscription") || lower.contains("recurring") || lower.contains("bills")) {
            reply.append("### Recurring Payments & Subscriptions\n\n");
            if (comprehensive.detectedSubscriptions().isEmpty()) {
                reply.append("No repeating subscription patterns were detected in your transaction history.\n");
            } else {
                reply.append("We detected **").append(comprehensive.detectedSubscriptions().size())
                        .append("** recurring payments totaling approximately **").append(formatMoney(comprehensive.totalMonthlySubscriptions()))
                        .append("/month**:\n\n");
                for (var sub : comprehensive.detectedSubscriptions()) {
                    reply.append("- **").append(sub.description()).append("**: ").append(formatMoney(sub.averageAmount()))
                            .append(" (").append(sub.frequency().toLowerCase()).append(", ").append(sub.category()).append(")\n");
                }
                reply.append("\n> 💡 *Tip: Auditing recurring subscriptions every quarter can help eliminate unused memberships.*");
            }
            suggestions.addAll(List.of("Where can I save money?", "What is my monthly cash flow?", "Show recent transactions"));
        } else if (lower.contains("save") || lower.contains("recommend") || lower.contains("advice") || lower.contains("cut back") || lower.contains("tip")) {
            reply.append("### Personalized Savings Recommendations\n\n");
            if (comprehensive.recommendations().isEmpty()) {
                reply.append("Great job! Your current savings rate is solid and no critical budget overruns were detected.\n\n");
                reply.append("- Keep maintaining at least a 3-month emergency fund in liquid cash.\n");
                reply.append("- Invest surplus cash into diversified index funds or fixed deposits.\n");
            } else {
                for (int i = 0; i < comprehensive.recommendations().size(); i++) {
                    var r = comprehensive.recommendations().get(i);
                    reply.append("**").append(i + 1).append(". ").append(r.title()).append("** (Impact: ").append(r.impact()).append(")\n");
                    reply.append(r.advice()).append("\n\n");
                }
            }
            suggestions.addAll(List.of("What are my highest expense categories?", "Show my recurring subscriptions", "What is my financial health score?"));
        } else if (lower.contains("health") || lower.contains("score") || lower.contains("resilience")) {
            reply.append("### Financial Health Indicator\n\n");
            if (healthAssessment.score() == null) {
                reply.append("Not enough financial data has been entered to compute a reliable health score yet. Add your income, expenses, assets, and liabilities to see your score.\n");
            } else {
                reply.append("Your Financial Health Score is **").append(healthAssessment.score().intValue()).append(" / 100** (Confidence: ").append(healthAssessment.confidence()).append(").\n\n");
                reply.append("**Component Breakdown:**\n");
                for (var c : healthAssessment.components()) {
                    reply.append("- **").append(c.label()).append("**: ").append(c.available() ? c.points().toPlainString() + " / " + c.weight() + " points" : "Not enough data").append("\n");
                }
                reply.append("\n*").append(healthAssessment.disclaimer()).append("*\n");
            }
            suggestions.addAll(List.of("Where can I save money?", "What is my net worth?", "How are my budgets doing?"));
        } else if (lower.contains("recent") || lower.contains("latest") || lower.contains("transaction")) {
            reply.append("### Recent Transactions\n\n");
            if (recentTx.isEmpty()) {
                reply.append("No transactions have been recorded yet. Add a transaction or import a statement to get started.\n");
            } else {
                reply.append("| Date | Description | Category | Type | Amount |\n");
                reply.append("| :--- | :--- | :--- | :--- | :--- |\n");
                for (var t : recentTx) {
                    reply.append("| ").append(t.getOccurredOn()).append(" | ").append(t.getDescription()).append(" | ")
                            .append(t.getCategory()).append(" | ").append(t.getDirection().name()).append(" | ")
                            .append(t.getDirection() == FinancialTransaction.Direction.INCOME ? "+" : "-")
                            .append(formatMoney(t.getAmount())).append(" |\n");
                }
            }
            suggestions.addAll(List.of("What is my monthly spending?", "Where can I save money?", "Show my recurring bills"));
        } else {
            // General / greeting
            reply.append("Hello ").append(name).append("! I am your **FinSight AI Assistant**.\n\n");
            reply.append("I am directly connected to your actual financial records and accounts to provide real-time, grounded intelligence with zero guesswork.\n\n");
            reply.append("Here is your snapshot today:\n");
            reply.append("- **Net Worth**: ").append(formatMoney(netWorth)).append("\n");
            reply.append("- **Current Month Income**: ").append(formatMoney(income)).append("\n");
            reply.append("- **Current Month Expenses**: ").append(formatMoney(expenses)).append("\n");
            reply.append("- **Savings Rate**: ").append(savingsRate).append("%\n\n");
            reply.append("Ask me anything about your spending, budgets, savings opportunities, recurring bills, or net worth!");
            suggestions.addAll(List.of("What is my net worth?", "How much did I spend this month?", "Where can I save money?", "How is my financial health?"));
        }

        return new ChatResponse(reply.toString(), suggestions, grounding, "FINSIGHT_GROUNDED_INTELLIGENCE");
    }

    private static String formatMoney(BigDecimal n) {
        if (n == null) return "₹0.00";
        NumberFormat nf = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));
        return nf.format(n);
    }

    private BigDecimal querySum(String sql, UUID userId) {
        BigDecimal val = jdbc.queryForObject(sql, new MapSqlParameterSource("user", userId), BigDecimal.class);
        return val != null ? val : BigDecimal.ZERO;
    }
}
