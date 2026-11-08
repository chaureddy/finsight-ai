package ai.finsight.paypal;

import ai.finsight.transaction.FinancialTransaction;
import ai.finsight.transaction.TransactionRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@Service
public class PayPalImportPersistenceService {
    private static final Set<String> SAFE_PAYMENT_CODES = Set.of(
            "T0000", "T0001", "T0002", "T0003", "T0004", "T0005", "T0006", "T0007", "T0008",
            "T0009", "T0010", "T0011", "T0012", "T0013", "T0014", "T0015", "T0016",
            "T0018", "T0019", "T0020", "T0021", "T0022", "T0023");
    private final PayPalConnectionRepository connections;
    private final PayPalImportRecordRepository imports;
    private final TransactionRepository transactions;
    private final boolean signedAmountDirectionVerified;

    public PayPalImportPersistenceService(PayPalConnectionRepository connections, PayPalImportRecordRepository imports,
            TransactionRepository transactions,
            @Value("${paypal.import.signed-amount-direction-verified:false}") boolean signedAmountDirectionVerified) {
        this.connections = connections; this.imports = imports; this.transactions = transactions;
        this.signedAmountDirectionVerified = signedAmountDirectionVerified;
    }

    public record Counts(int importedCount, int duplicateCount, int reviewCount) {}

    @Transactional
    public Counts persist(UUID ownerId, List<PayPalTransactionData> incoming) {
        PayPalConnection connection = connections.lockByEnvironment("SANDBOX").orElse(null);
        if (connection != null && !connection.getUserId().equals(ownerId)) throw conflict();
        if (connection == null) {
            try {
                connection = connections.saveAndFlush(new PayPalConnection(ownerId));
            } catch (DataIntegrityViolationException ex) {
                throw conflict();
            }
        }
        connection.verifiedNow();
        Set<String> seen = new HashSet<>();
        int imported = 0, duplicate = 0, review = 0;
        for (PayPalTransactionData item : incoming) {
            String fingerprint = item.sourceFingerprint();
            if (!seen.add(fingerprint) || imports.existsByConnection_IdAndSourceFingerprint(connection.getId(), fingerprint)) {
                duplicate++; continue;
            }
            String reason = reviewReason(item);
            if (reason != null) {
                imports.save(new PayPalImportRecord(connection, safeForStorage(item), PayPalImportRecord.State.REVIEW, reason, null));
                review++; continue;
            }
            FinancialTransaction.Direction direction = item.signedAmount().signum() > 0
                    ? FinancialTransaction.Direction.INCOME : FinancialTransaction.Direction.EXPENSE;
            BigDecimal amount = item.signedAmount().abs();
            String externalId = item.transactionId();
            FinancialTransaction saved = transactions.saveAndFlush(new FinancialTransaction(ownerId,
                    "PayPal transaction", "PayPal", amount, direction, item.occurredOn(), "PAYPAL", externalId, "INR"));
            imports.save(new PayPalImportRecord(connection, item, PayPalImportRecord.State.IMPORTED, null, saved.getId()));
            imported++;
        }
        return new Counts(imported, duplicate, review);
    }

    public void assertCanConnect(UUID ownerId) {
        connections.findByEnvironment("SANDBOX").filter(c -> !c.getUserId().equals(ownerId)).ifPresent(c -> { throw conflict(); });
    }

    public ConnectionStatus status(UUID ownerId, boolean configured) {
        return connections.findByUserId(ownerId)
                .filter(c -> configured)
                .map(c -> new ConnectionStatus(true, true, "CONNECTED", c.getCreatedAt(), c.getLastVerifiedAt()))
                .orElseGet(() -> new ConnectionStatus(configured, false, configured ? "NOT_CONNECTED" : "NOT_CONFIGURED", null, null));
    }

    @Transactional
    public void disconnect(UUID ownerId) {
        connections.findByUserId(ownerId).ifPresent(connections::delete);
    }

    @Transactional
    public void dismissReview(UUID ownerId, UUID reviewId) {
        PayPalConnection connection = connections.findByUserId(ownerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No PayPal connection found."));
        PayPalImportRecord record = imports.findById(reviewId)
                .filter(r -> r.getConnection().getId().equals(connection.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Review record not found."));
        record.dismiss();
        imports.save(record);
    }

    @Transactional
    public FinancialTransaction approveReview(UUID ownerId, UUID reviewId, String description, String category, FinancialTransaction.Direction direction) {
        PayPalConnection connection = connections.findByUserId(ownerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No PayPal connection found."));
        PayPalImportRecord record = imports.findById(reviewId)
                .filter(r -> r.getConnection().getId().equals(connection.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Review record not found."));
        if (record.getState() != PayPalImportRecord.State.REVIEW) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Record is not in review state.");
        }
        BigDecimal amount = record.getAmount() != null ? record.getAmount().abs() : BigDecimal.ONE;
        LocalDate date = record.getOccurredOn() != null ? record.getOccurredOn() : LocalDate.now();
        String desc = (description != null && !description.isBlank()) ? description.trim() : "PayPal approved record";
        String cat = (category != null && !category.isBlank()) ? category.trim() : "PayPal";
        FinancialTransaction.Direction dir = direction != null ? direction : FinancialTransaction.Direction.EXPENSE;
        String ref = record.getPaypalTransactionId() != null ? record.getPaypalTransactionId() : record.getSourceFingerprint();
        FinancialTransaction tx = transactions.save(new FinancialTransaction(ownerId, desc, cat, amount, dir, date, "PAYPAL", ref, "INR"));
        record.resolve(tx.getId());
        imports.save(record);
        return tx;
    }

    @Transactional
    public FinancialTransaction recordOrderCapture(UUID ownerId, PayPalCaptureResponse capture, String description, String category, FinancialTransaction.Direction direction) {
        PayPalConnection connection = connections.lockByEnvironment("SANDBOX").orElse(null);
        if (connection != null && !connection.getUserId().equals(ownerId)) throw conflict();
        if (connection == null) {
            try {
                connection = connections.saveAndFlush(new PayPalConnection(ownerId));
            } catch (DataIntegrityViolationException ex) {
                throw conflict();
            }
        }
        connection.verifiedNow();

        String desc = (description != null && !description.isBlank())
                ? description.trim()
                : "PayPal Order " + capture.orderId();
        String cat = (category != null && !category.isBlank())
                ? category.trim()
                : "Shopping";
        FinancialTransaction.Direction dir = (direction != null)
                ? direction
                : FinancialTransaction.Direction.EXPENSE;
        BigDecimal amount = (capture.amount() != null) ? capture.amount().abs() : BigDecimal.ONE;
        String currency = (capture.currency() != null && !capture.currency().isBlank())
                ? capture.currency().toUpperCase()
                : "INR";

        String ref = (capture.captureId() != null && !capture.captureId().isBlank())
                ? capture.captureId()
                : capture.orderId();

        FinancialTransaction saved = transactions.saveAndFlush(new FinancialTransaction(
                ownerId,
                desc,
                cat,
                amount,
                dir,
                LocalDate.now(),
                "PAYPAL",
                ref,
                currency
        ));

        String fingerprint = "ORDER-" + capture.orderId() + "-" + ref;
        PayPalTransactionData data = new PayPalTransactionData(
                ref,
                "S",
                "T0006",
                LocalDate.now(),
                dir == FinancialTransaction.Direction.INCOME ? amount : amount.negate(),
                currency,
                capture.fee(),
                fingerprint
        );
        imports.save(new PayPalImportRecord(connection, data, PayPalImportRecord.State.IMPORTED, null, saved.getId()));

        return saved;
    }

    public record ConnectionStatus(boolean configured, boolean connected, String state, Instant connectedAt, Instant lastVerifiedAt) {}
    public record ReviewItem(UUID id, String paypalTransactionId, java.time.LocalDate occurredOn, BigDecimal amount, String currencyCode, String eventCode, String reason) {}
    public java.util.List<ReviewItem> reviews(UUID ownerId) {
        return connections.findByUserId(ownerId).map(connection -> imports.findAllByConnection_IdAndStateOrderByCreatedAtDesc(connection.getId(), PayPalImportRecord.State.REVIEW)
                .stream().map(item -> new ReviewItem(item.getId(), item.getPaypalTransactionId(), item.getOccurredOn(), item.getAmount(), item.getCurrencyCode(), item.getEventCode(), item.getReviewReason())).toList())
                .orElseGet(java.util.List::of);
    }

    private String reviewReason(PayPalTransactionData x) {
        if (!validId(x.transactionId())) return "MISSING_OR_INVALID_TRANSACTION_ID";
        if (x.status() == null || !x.status().equals("S")) return "UNSETTLED_OR_REVERSED";
        if (x.eventCode() == null || !SAFE_PAYMENT_CODES.contains(x.eventCode())) return "UNSUPPORTED_EVENT";
        if (x.occurredOn() == null) return "MISSING_DATE";
        if (!"INR".equals(x.currencyCode())) return "UNSUPPORTED_CURRENCY";
        if (x.signedAmount() == null || x.signedAmount().signum() == 0) return "AMBIGUOUS_AMOUNT";
        if (!signedAmountDirectionVerified) return "UNVERIFIED_AMOUNT_DIRECTION";
        if (x.signedAmount().precision() > 19 || x.signedAmount().stripTrailingZeros().scale() > 2) return "UNSUPPORTED_PRECISION";
        if (x.feeAmount() != null && x.feeAmount().signum() != 0) return "FEE_OR_CURRENCY_MISMATCH";
        return null;
    }
    private static PayPalTransactionData safeForStorage(PayPalTransactionData x) {
        BigDecimal amount = x.signedAmount();
        if (amount != null && (amount.precision() > 19 || amount.stripTrailingZeros().scale() > 2)) amount = null;
        String currency = x.currencyCode() != null && x.currencyCode().matches("[A-Z]{3}") ? x.currencyCode() : null;
        return new PayPalTransactionData(x.transactionId(), x.status(), x.eventCode(), x.occurredOn(), amount, currency, x.feeAmount(), x.sourceFingerprint());
    }
    private static boolean validId(String id) { return id != null && id.matches("[A-Za-z0-9_]{17,24}"); }
    private static ResponseStatusException conflict() { return new ResponseStatusException(HttpStatus.CONFLICT, "This Sandbox reporting source is already linked to another FinSight user."); }
}
