package ai.finsight.paypal;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "paypal_import_record", uniqueConstraints = @UniqueConstraint(name = "paypal_import_fingerprint_uq", columnNames = {"connection_id", "source_fingerprint"}))
public class PayPalImportRecord {
    public enum State { IMPORTED, REVIEW }
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "connection_id", nullable = false) private PayPalConnection connection;
    @Column(name = "paypal_transaction_id", length = 24) private String paypalTransactionId;
    @Column(name = "source_fingerprint", nullable = false, length = 64) private String sourceFingerprint;
    @Column(name = "occurred_on") private LocalDate occurredOn;
    @Column(precision = 19, scale = 2) private BigDecimal amount;
    @Column(name = "currency_code", length = 3) private String currencyCode;
    @Column(name = "event_code", length = 5) private String eventCode;
    @Column(name = "paypal_status", length = 1) private String paypalStatus;
    @Enumerated(EnumType.STRING) @Column(name = "import_state", nullable = false, length = 12) private State state;
    @Column(name = "review_reason", length = 40) private String reviewReason;
    @Column(name = "financial_transaction_id", unique = true) private UUID financialTransactionId;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected PayPalImportRecord() {}
    PayPalImportRecord(PayPalConnection connection, PayPalTransactionData data, State state, String reason, UUID transactionId) {
        this.id = UUID.randomUUID(); this.connection = connection; this.paypalTransactionId = data.transactionId(); this.sourceFingerprint = data.sourceFingerprint();
        this.occurredOn = data.occurredOn(); this.amount = data.signedAmount(); this.currencyCode = data.currencyCode();
        this.eventCode = data.eventCode(); this.paypalStatus = data.status(); this.state = state;
        this.reviewReason = reason; this.financialTransactionId = transactionId; this.createdAt = Instant.now();
    }
    public UUID getId() { return id; }
    public String getPaypalTransactionId() { return paypalTransactionId; }
    public LocalDate getOccurredOn() { return occurredOn; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrencyCode() { return currencyCode; }
    public String getEventCode() { return eventCode; }
    public State getState() { return state; }
    public String getReviewReason() { return reviewReason; }
    public Instant getCreatedAt() { return createdAt; }
}
