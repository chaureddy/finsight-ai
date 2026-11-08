package ai.finsight.intelligence;

import ai.finsight.budget.BudgetService;
import ai.finsight.recurring.RecurringTransaction;
import ai.finsight.recurring.RecurringTransactionRepository;
import ai.finsight.transaction.FinancialTransaction;
import ai.finsight.transaction.TransactionRepository;
import ai.finsight.user.UserRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class AdvancedFinancialAnalyticsService {
    private final UserRepository users;
    private final TransactionRepository transactions;
    private final RecurringTransactionRepository recurring;
    private final BudgetService budgets;
    private final NamedParameterJdbcTemplate jdbc;

    public AdvancedFinancialAnalyticsService(UserRepository users,
                                             TransactionRepository transactions,
                                             RecurringTransactionRepository recurring,
                                             BudgetService budgets,
                                             NamedParameterJdbcTemplate jdbc) {
        this.users = users;
        this.transactions = transactions;
        this.recurring = recurring;
        this.budgets = budgets;
        this.jdbc = jdbc;
    }

    public record MerchantSpend(String merchant, BigDecimal totalAmount, int transactionCount, String topCategory) {}
    public record DetectedRecurring(String description, BigDecimal averageAmount, String category, String frequency, int occurrences, LocalDate lastSeen) {}
    public record RefundDetail(String description, BigDecimal amount, LocalDate date, String sourceProvider) {}
    public record SavingsRecommendation(String title, String category, String impact, String advice, BigDecimal potentialSavings) {}
    public record ComprehensiveIntelligence(
            List<MerchantSpend> topMerchants,
            List<DetectedRecurring> detectedSubscriptions,
            List<RefundDetail> refunds,
            List<SavingsRecommendation> recommendations,
            BigDecimal totalMonthlySubscriptions,
            BigDecimal highestCategorySpend,
            String highestSpendCategoryName
    ) {}

    public record PayPalIntelligence(
            BigDecimal totalSpend,
            BigDecimal totalIncome,
            int transactionCount,
            BigDecimal totalFees,
            List<MerchantSpend> topMerchants,
            List<DetectedRecurring> recurringSubscriptions,
            List<FinancialTransaction> recentTransactions
    ) {}

    private UUID owner(String email) {
        return users.findByEmailIgnoreCase(email).orElseThrow().getId();
    }

    @Transactional(readOnly = true)
    public ComprehensiveIntelligence analyze(String email) {
        UUID ownerId = owner(email);
        List<FinancialTransaction> allTx = transactions.findAllByUserIdOrderByOccurredOnDesc(ownerId);
        YearMonth currentMonth = YearMonth.now();
        LocalDate fromCurrent = currentMonth.atDay(1);
        LocalDate toCurrent = currentMonth.plusMonths(1).atDay(1);

        // 1. Merchant Spend Analysis
        Map<String, List<FinancialTransaction>> merchantGroups = new HashMap<>();
        for (FinancialTransaction tx : allTx) {
            if (tx.getDirection() == FinancialTransaction.Direction.EXPENSE && !tx.getOccurredOn().isBefore(fromCurrent)) {
                String merchant = extractMerchant(tx.getDescription());
                merchantGroups.computeIfAbsent(merchant, k -> new ArrayList<>()).add(tx);
            }
        }
        List<MerchantSpend> topMerchants = new ArrayList<>();
        for (Map.Entry<String, List<FinancialTransaction>> entry : merchantGroups.entrySet()) {
            BigDecimal sum = entry.getValue().stream().map(FinancialTransaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            String topCat = entry.getValue().stream().max(Comparator.comparing(FinancialTransaction::getAmount)).map(FinancialTransaction::getCategory).orElse("General");
            topMerchants.add(new MerchantSpend(entry.getKey(), sum.setScale(2, RoundingMode.HALF_UP), entry.getValue().size(), topCat));
        }
        topMerchants.sort(Comparator.comparing(MerchantSpend::totalAmount).reversed());

        // 2. Automated Recurring Payment Detection
        List<DetectedRecurring> detected = detectRecurringPatterns(allTx);

        // 3. Refund & Reversal Analysis
        List<RefundDetail> refunds = new ArrayList<>();
        for (FinancialTransaction tx : allTx) {
            String desc = tx.getDescription().toLowerCase();
            if (tx.getDirection() == FinancialTransaction.Direction.INCOME &&
                    ("Refund".equalsIgnoreCase(tx.getCategory()) || desc.contains("refund") || desc.contains("reversal") || desc.contains("cashback"))) {
                refunds.add(new RefundDetail(tx.getDescription(), tx.getAmount().setScale(2, RoundingMode.HALF_UP), tx.getOccurredOn(), tx.getSourceProvider()));
            }
        }

        // 4. Personalized Savings Recommendations
        List<SavingsRecommendation> recommendations = generateRecommendations(ownerId, email, allTx, topMerchants, detected);

        // Totals
        BigDecimal totalSub = detected.stream().map(DetectedRecurring::averageAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        String topCatName = "None";
        BigDecimal topCatVal = BigDecimal.ZERO;
        List<Object[]> cats = transactions.categoryTotals(ownerId, fromCurrent, toCurrent, FinancialTransaction.Direction.EXPENSE);
        if (!cats.isEmpty()) {
            topCatName = (String) cats.get(0)[0];
            topCatVal = (BigDecimal) cats.get(0)[1];
        }

        return new ComprehensiveIntelligence(
                topMerchants.stream().limit(8).toList(),
                detected,
                refunds.stream().limit(10).toList(),
                recommendations,
                totalSub.setScale(2, RoundingMode.HALF_UP),
                topCatVal.setScale(2, RoundingMode.HALF_UP),
                topCatName
        );
    }

    @Transactional(readOnly = true)
    public PayPalIntelligence analyzePayPal(UUID ownerId) {
        List<FinancialTransaction> allTx = transactions.findAllByUserIdOrderByOccurredOnDesc(ownerId);
        List<FinancialTransaction> paypalTx = allTx.stream()
                .filter(tx -> "PAYPAL".equalsIgnoreCase(tx.getSourceProvider()) ||
                        (tx.getDescription() != null && tx.getDescription().toLowerCase().contains("paypal")))
                .toList();

        BigDecimal spend = BigDecimal.ZERO;
        BigDecimal income = BigDecimal.ZERO;
        Map<String, List<FinancialTransaction>> merchantGroups = new HashMap<>();

        for (FinancialTransaction tx : paypalTx) {
            if (tx.getDirection() == FinancialTransaction.Direction.EXPENSE) {
                spend = spend.add(tx.getAmount());
                String merchant = extractMerchant(tx.getDescription());
                merchantGroups.computeIfAbsent(merchant, k -> new ArrayList<>()).add(tx);
            } else if (tx.getDirection() == FinancialTransaction.Direction.INCOME) {
                income = income.add(tx.getAmount());
            }
        }

        List<MerchantSpend> topMerchants = new ArrayList<>();
        for (Map.Entry<String, List<FinancialTransaction>> entry : merchantGroups.entrySet()) {
            BigDecimal sum = entry.getValue().stream().map(FinancialTransaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            String topCat = entry.getValue().stream().max(Comparator.comparing(FinancialTransaction::getAmount)).map(FinancialTransaction::getCategory).orElse("PayPal");
            topMerchants.add(new MerchantSpend(entry.getKey(), sum.setScale(2, RoundingMode.HALF_UP), entry.getValue().size(), topCat));
        }
        topMerchants.sort(Comparator.comparing(MerchantSpend::totalAmount).reversed());

        List<DetectedRecurring> recurring = detectRecurringPatterns(paypalTx);
        BigDecimal estimatedFees = spend.multiply(new BigDecimal("0.029")).setScale(2, RoundingMode.HALF_UP);

        return new PayPalIntelligence(
                spend.setScale(2, RoundingMode.HALF_UP),
                income.setScale(2, RoundingMode.HALF_UP),
                paypalTx.size(),
                estimatedFees,
                topMerchants.stream().limit(5).toList(),
                recurring,
                paypalTx.stream().limit(10).toList()
        );
    }

    private List<DetectedRecurring> detectRecurringPatterns(List<FinancialTransaction> transactions) {
        Map<String, List<FinancialTransaction>> byMerchant = new HashMap<>();
        for (FinancialTransaction tx : transactions) {
            if (tx.getDirection() == FinancialTransaction.Direction.EXPENSE) {
                String m = extractMerchant(tx.getDescription());
                byMerchant.computeIfAbsent(m, k -> new ArrayList<>()).add(tx);
            }
        }

        List<DetectedRecurring> result = new ArrayList<>();
        for (Map.Entry<String, List<FinancialTransaction>> entry : byMerchant.entrySet()) {
            List<FinancialTransaction> list = entry.getValue();
            if (list.size() < 2) continue;

            list.sort(Comparator.comparing(FinancialTransaction::getOccurredOn));
            BigDecimal firstAmt = list.get(0).getAmount();
            boolean amountsSimilar = true;
            for (FinancialTransaction tx : list) {
                BigDecimal diff = tx.getAmount().subtract(firstAmt).abs();
                if (diff.compareTo(new BigDecimal("50.00")) > 0 && diff.divide(firstAmt, 2, RoundingMode.HALF_UP).compareTo(new BigDecimal("0.10")) > 0) {
                    amountsSimilar = false;
                    break;
                }
            }

            if (amountsSimilar) {
                long daysBetween = ChronoUnit.DAYS.between(list.get(0).getOccurredOn(), list.get(list.size() - 1).getOccurredOn());
                long avgInterval = daysBetween / (list.size() - 1);
                String freq = (avgInterval >= 25 && avgInterval <= 35) ? "MONTHLY"
                        : (avgInterval >= 6 && avgInterval <= 8) ? "WEEKLY"
                        : (avgInterval >= 350 && avgInterval <= 380) ? "YEARLY"
                        : "PERIODIC";

                if (!"PERIODIC".equals(freq) || list.size() >= 3) {
                    BigDecimal avg = list.stream().map(FinancialTransaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add)
                            .divide(BigDecimal.valueOf(list.size()), 2, RoundingMode.HALF_UP);
                    result.add(new DetectedRecurring(entry.getKey(), avg, list.get(0).getCategory(), freq, list.size(), list.get(list.size() - 1).getOccurredOn()));
                }
            }
        }
        result.sort(Comparator.comparing(DetectedRecurring::averageAmount).reversed());
        return result.stream().limit(10).toList();
    }

    private List<SavingsRecommendation> generateRecommendations(UUID ownerId, String email, List<FinancialTransaction> allTx, List<MerchantSpend> topMerchants, List<DetectedRecurring> detected) {
        List<SavingsRecommendation> recs = new ArrayList<>();
        YearMonth current = YearMonth.now();
        LocalDate from = current.atDay(1);
        LocalDate to = current.plusMonths(1).atDay(1);

        BigDecimal income = transactions.sumForOwnerAndPeriod(ownerId, from, to, FinancialTransaction.Direction.INCOME);
        BigDecimal expenses = transactions.sumForOwnerAndPeriod(ownerId, from, to, FinancialTransaction.Direction.EXPENSE);

        // 1. Savings rate check
        if (income.signum() > 0) {
            BigDecimal savings = income.subtract(expenses);
            BigDecimal rate = savings.multiply(BigDecimal.valueOf(100)).divide(income, 2, RoundingMode.HALF_UP);
            if (rate.compareTo(new BigDecimal("20.00")) < 0) {
                BigDecimal targetSavings = income.multiply(new BigDecimal("0.20")).setScale(2, RoundingMode.HALF_UP);
                BigDecimal shortfall = targetSavings.subtract(savings).max(BigDecimal.ZERO);
                recs.add(new SavingsRecommendation(
                        "Boost monthly savings towards 20% target",
                        "Savings Target",
                        "High",
                        "Your current month savings rate is " + rate + "%. Aiming for 20% would build wealth faster and requires trimming ₹" + shortfall + " from discretionary spending.",
                        shortfall
                ));
            }
        }

        // 2. Budget overruns
        try {
            var userBudgets = budgets.list(email);
            for (var b : userBudgets) {
                if ("EXCEEDED".equals(b.status())) {
                    BigDecimal over = b.amountSpent().subtract(b.monthlyLimit());
                    recs.add(new SavingsRecommendation(
                            "Control spending in " + b.category(),
                            "Budget Alert",
                            "High",
                            "You have exceeded your monthly limit for " + b.category() + " by ₹" + over + ". Pause non-essential purchases in this category for the rest of the month.",
                            over
                    ));
                }
            }
        } catch (Exception ignored) {}

        // 3. Emergency fund assessment
        BigDecimal cash = jdbc.queryForObject(
                "SELECT COALESCE(SUM(current_value),0) FROM financial_asset WHERE user_id=:user AND category IN ('Cash','Bank Account')",
                new MapSqlParameterSource("user", ownerId),
                BigDecimal.class
        );
        if (cash != null && expenses.signum() > 0) {
            BigDecimal targetEmergency = expenses.multiply(BigDecimal.valueOf(3)).setScale(2, RoundingMode.HALF_UP);
            if (cash.compareTo(targetEmergency) < 0) {
                BigDecimal gap = targetEmergency.subtract(cash);
                recs.add(new SavingsRecommendation(
                        "Establish 3-month emergency fund",
                        "Financial Resilience",
                        "Critical",
                        "Your current liquid cash (₹" + cash + ") is below 3 months of baseline expenses (₹" + targetEmergency + "). Prioritize liquid deposits before higher-risk investments.",
                        gap
                ));
            }
        }

        // 4. Subscription audit
        if (!detected.isEmpty()) {
            BigDecimal subTotal = detected.stream().map(DetectedRecurring::averageAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (subTotal.compareTo(new BigDecimal("1500.00")) > 0) {
                recs.add(new SavingsRecommendation(
                        "Review recurring subscriptions",
                        "Subscriptions",
                        "Medium",
                        "We detected " + detected.size() + " recurring charges totaling ₹" + subTotal + " monthly. Cancel unused services to unlock immediate monthly savings.",
                        subTotal.multiply(new BigDecimal("0.30")).setScale(2, RoundingMode.HALF_UP)
                ));
            }
        }

        // 5. High-interest liability check
        List<Map<String, Object>> highInterestDebts = jdbc.queryForList(
                "SELECT name, interest_rate, outstanding_amount FROM financial_liability WHERE user_id=:user AND interest_rate >= 12.0 ORDER BY interest_rate DESC",
                new MapSqlParameterSource("user", ownerId)
        );
        if (!highInterestDebts.isEmpty()) {
            Map<String, Object> topDebt = highInterestDebts.get(0);
            recs.add(new SavingsRecommendation(
                    "Accelerate payoff of high-interest debt (" + topDebt.get("name") + ")",
                    "Debt Elimination",
                    "High",
                    "Your " + topDebt.get("name") + " has a " + topDebt.get("interest_rate") + "% interest rate with ₹" + topDebt.get("outstanding_amount") + " outstanding. Extra payments here provide a guaranteed return equal to the interest rate.",
                    null
            ));
        }

        return recs;
    }

    private static String extractMerchant(String desc) {
        if (desc == null || desc.isBlank()) return "Unknown";
        String clean = desc.trim();
        // Remove common prefixes
        String[] prefixes = {"pos ", "upi/", "neft/", "imps/", "ach/", "paytm*", "paypal *", "visa ", "mastercard "};
        String lower = clean.toLowerCase();
        for (String p : prefixes) {
            if (lower.startsWith(p)) {
                clean = clean.substring(p.length()).trim();
                break;
            }
        }
        // Take first 2-3 words or until special separator
        String[] parts = clean.split("[-/|@#]");
        String candidate = parts[0].trim();
        if (candidate.length() > 30) candidate = candidate.substring(0, 30);
        return candidate.isEmpty() ? "Unknown" : candidate;
    }
}

