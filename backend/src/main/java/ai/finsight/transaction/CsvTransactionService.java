package ai.finsight.transaction;

import ai.finsight.account.UserAccountConnection;
import ai.finsight.account.UserAccountConnectionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class CsvTransactionService {
    private final TransactionRepository transactions;
    private final UserAccountConnectionRepository accounts;

    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("yyyy.MM.dd")
    );

    public CsvTransactionService(TransactionRepository transactions, UserAccountConnectionRepository accounts) {
        this.transactions = transactions;
        this.accounts = accounts;
    }

    public record ImportSummary(int importedCount, int duplicateCount, int skippedCount, List<String> warnings) {}

    @Transactional
    public ImportSummary importCsv(UUID ownerId, String csvContent, String accountLabel) {
        if (csvContent == null || csvContent.isBlank()) {
            return new ImportSummary(0, 0, 0, List.of("CSV content is empty."));
        }

        List<String> lines = parseLines(csvContent);
        if (lines.isEmpty()) {
            return new ImportSummary(0, 0, 0, List.of("No data rows found in CSV."));
        }

        List<String> headers = parseCsvRow(lines.get(0)).stream().map(h -> h.toLowerCase().trim().replace("\"", "")).toList();
        int dateCol = findColumnIndex(headers, "date", "occurred", "transaction date", "txn date", "time");
        int descCol = findColumnIndex(headers, "description", "details", "memo", "name", "narration", "payee");
        int amountCol = findColumnIndex(headers, "amount", "gross", "value", "net");
        int debitCol = findColumnIndex(headers, "debit", "withdrawal", "expense");
        int creditCol = findColumnIndex(headers, "credit", "deposit", "income");
        int typeCol = findColumnIndex(headers, "type", "direction", "kind", "dr/cr");
        int catCol = findColumnIndex(headers, "category");
        int refCol = findColumnIndex(headers, "reference", "transaction id", "ref no", "id", "txn id");
        int currCol = findColumnIndex(headers, "currency", "curr");

        if (dateCol < 0 || (descCol < 0 && amountCol < 0 && debitCol < 0 && creditCol < 0)) {
            return new ImportSummary(0, 0, lines.size() - 1, List.of("Unable to detect required CSV columns (Date and Amount/Description required)."));
        }

        Set<String> seenFingerprints = new HashSet<>();
        List<String> warnings = new ArrayList<>();
        int imported = 0, duplicates = 0, skipped = 0;

        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty()) continue;
            List<String> row = parseCsvRow(line);

            LocalDate date = parseDate(getCell(row, dateCol));
            if (date == null) {
                skipped++;
                warnings.add("Row " + (i + 1) + ": Invalid date format '" + getCell(row, dateCol) + "'.");
                continue;
            }

            String desc = descCol >= 0 ? getCell(row, descCol) : "CSV Transaction";
            if (desc.isBlank()) desc = "CSV Transaction";
            if (desc.length() > 120) desc = desc.substring(0, 120);

            BigDecimal amount = null;
            FinancialTransaction.Direction direction = FinancialTransaction.Direction.EXPENSE;

            if (debitCol >= 0 && creditCol >= 0) {
                BigDecimal debit = parseDecimal(getCell(row, debitCol));
                BigDecimal credit = parseDecimal(getCell(row, creditCol));
                if (credit != null && credit.signum() > 0) {
                    amount = credit;
                    direction = FinancialTransaction.Direction.INCOME;
                } else if (debit != null && debit.signum() > 0) {
                    amount = debit;
                    direction = FinancialTransaction.Direction.EXPENSE;
                }
            }

            if (amount == null && amountCol >= 0) {
                BigDecimal parsedAmt = parseDecimal(getCell(row, amountCol));
                if (parsedAmt != null) {
                    if (parsedAmt.signum() < 0) {
                        amount = parsedAmt.abs();
                        direction = FinancialTransaction.Direction.EXPENSE;
                    } else {
                        amount = parsedAmt;
                        String typeStr = typeCol >= 0 ? getCell(row, typeCol).toUpperCase() : "";
                        if (typeStr.contains("RECEIVED") || typeStr.contains("RECEIVE") || typeStr.contains("INC") || typeStr.contains("CREDIT") || typeStr.contains("CR") || typeStr.contains("DEP")) {
                            direction = FinancialTransaction.Direction.INCOME;
                        } else if (typeStr.contains("SENT") || typeStr.contains("EXP") || typeStr.contains("DEBIT") || typeStr.contains("DR") || typeStr.contains("PAY") || typeStr.contains("WITHDRAW")) {
                            direction = FinancialTransaction.Direction.EXPENSE;
                        } else {
                            direction = FinancialTransaction.Direction.EXPENSE;
                        }
                    }
                }
            }

            if (amount == null || amount.signum() <= 0) {
                skipped++;
                continue;
            }

            amount = amount.setScale(2, RoundingMode.HALF_UP);
            String category = catCol >= 0 && !getCell(row, catCol).isBlank() ? getCell(row, catCol).trim() : guessCategory(desc, direction);
            if (category.length() > 60) category = category.substring(0, 60);

            String currency = currCol >= 0 && !getCell(row, currCol).isBlank() ? getCell(row, currCol).trim().toUpperCase() : "INR";
            if (!currency.matches("[A-Z]{3}")) currency = "INR";

            String rawRef = refCol >= 0 ? getCell(row, refCol).trim() : "";
            String fingerprint = rawRef.isBlank() ? computeFingerprint(date, amount, direction, desc) : rawRef;
            if (fingerprint.length() > 64) fingerprint = fingerprint.substring(0, 64);

            if (!seenFingerprints.add(fingerprint) || transactions.existsByUserIdAndSourceProviderAndExternalReference(ownerId, "CSV_IMPORT", fingerprint)) {
                duplicates++;
                continue;
            }

            FinancialTransaction tx = new FinancialTransaction(ownerId, desc, category, amount, direction, date, "CSV_IMPORT", fingerprint, currency);
            transactions.save(tx);
            imported++;
        }

        // Record or update user account connection for CSV
        String label = (accountLabel != null && !accountLabel.isBlank()) ? accountLabel.trim() : "CSV Statement";
        UserAccountConnection conn = accounts.findByUserIdAndProviderAndAccountName(ownerId, "CSV_STATEMENT", label)
                .orElseGet(() -> new UserAccountConnection(ownerId, "CSV_STATEMENT", label, "CSV Upload", "CONNECTED", null, "{}"));
        conn.updateStatus("CONNECTED", "{\"importedCount\":" + imported + ",\"lastImportAt\":\"" + LocalDate.now() + "\"}");
        accounts.save(conn);

        return new ImportSummary(imported, duplicates, skipped, warnings.stream().limit(10).toList());
    }

    public String exportCsv(UUID ownerId) {
        List<FinancialTransaction> list = transactions.findAllByUserIdOrderByOccurredOnDesc(ownerId);
        StringBuilder sb = new StringBuilder();
        sb.append("ID,Date,Description,Category,Type,Amount,Currency,Source,Reference\n");
        for (FinancialTransaction tx : list) {
            sb.append(escape(tx.getId().toString())).append(",")
                    .append(tx.getOccurredOn().toString()).append(",")
                    .append(escape(tx.getDescription())).append(",")
                    .append(escape(tx.getCategory())).append(",")
                    .append(tx.getDirection().name()).append(",")
                    .append(tx.getAmount().setScale(2, RoundingMode.HALF_UP).toPlainString()).append(",")
                    .append(tx.getCurrencyCode() != null ? tx.getCurrencyCode() : "INR").append(",")
                    .append(escape(tx.getSourceProvider() != null ? tx.getSourceProvider() : "MANUAL")).append(",")
                    .append(escape(tx.getExternalReference() != null ? tx.getExternalReference() : "")).append("\n");
        }
        return sb.toString();
    }

    private static String escape(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private static int findColumnIndex(List<String> headers, String... candidates) {
        for (int i = 0; i < headers.size(); i++) {
            String h = headers.get(i);
            for (String cand : candidates) {
                if (h.equals(cand) || h.contains(cand)) return i;
            }
        }
        return -1;
    }

    private static String getCell(List<String> row, int index) {
        if (index < 0 || index >= row.size()) return "";
        return row.get(index).trim().replace("\"", "");
    }

    private static LocalDate parseDate(String val) {
        if (val == null || val.isBlank()) return null;
        String cleaned = val.trim().split("T")[0].split(" ")[0];
        for (DateTimeFormatter fmt : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(cleaned, fmt);
            } catch (Exception ignored) {}
        }
        return null;
    }

    private static BigDecimal parseDecimal(String val) {
        if (val == null || val.isBlank()) return null;
        try {
            String sanitized = val.replaceAll("[^0-9.\\-]", "");
            if (sanitized.isBlank() || sanitized.equals("-") || sanitized.equals(".")) return null;
            return new BigDecimal(sanitized);
        } catch (Exception e) {
            return null;
        }
    }

    private static String guessCategory(String desc, FinancialTransaction.Direction dir) {
        String lower = desc.toLowerCase();
        if (dir == FinancialTransaction.Direction.INCOME) {
            if (lower.contains("salary") || lower.contains("payroll")) return "Salary";
            if (lower.contains("freelance") || lower.contains("consulting") || lower.contains("client")) return "Freelance";
            if (lower.contains("dividend") || lower.contains("interest")) return "Investment Return";
            if (lower.contains("refund") || lower.contains("reimbursement")) return "Refund";
            return "Other Income";
        }
        if (lower.contains("rent") || lower.contains("housing") || lower.contains("maintenance")) return "Housing";
        if (lower.contains("grocery") || lower.contains("supermarket") || lower.contains("mart") || lower.contains("food")) return "Groceries";
        if (lower.contains("electric") || lower.contains("water") || lower.contains("gas") || lower.contains("internet") || lower.contains("wifi") || lower.contains("mobile")) return "Utilities";
        if (lower.contains("uber") || lower.contains("ola") || lower.contains("fuel") || lower.contains("petrol") || lower.contains("train") || lower.contains("metro")) return "Transportation";
        if (lower.contains("hospital") || lower.contains("pharmacy") || lower.contains("clinic") || lower.contains("med")) return "Healthcare";
        if (lower.contains("netflix") || lower.contains("prime") || lower.contains("spotify") || lower.contains("movie")) return "Entertainment";
        if (lower.contains("restaurant") || lower.contains("cafe") || lower.contains("swiggy") || lower.contains("zomato")) return "Dining";
        if (lower.contains("amazon") || lower.contains("flipkart") || lower.contains("shopping") || lower.contains("myntra")) return "Shopping";
        return "General Expense";
    }

    private static String computeFingerprint(LocalDate date, BigDecimal amount, FinancialTransaction.Direction dir, String desc) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String raw = date + "|" + amount.toPlainString() + "|" + dir.name() + "|" + desc.toLowerCase().trim();
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 16; i++) { // 32 hex chars
                hex.append(String.format("%02x", hash[i]));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            return UUID.randomUUID().toString().replace("-", "");
        }
    }

    private static List<String> parseLines(String text) {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new StringReader(text))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) lines.add(line);
            }
        } catch (Exception ignored) {}
        return lines;
    }

    private static List<String> parseCsvRow(String line) {
        List<String> tokens = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                tokens.add(sb.toString().trim());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        tokens.add(sb.toString().trim());
        return tokens;
    }
}
