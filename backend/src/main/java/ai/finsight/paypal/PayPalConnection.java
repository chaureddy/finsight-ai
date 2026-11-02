package ai.finsight.paypal;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "paypal_sandbox_connection")
public class PayPalConnection {
    @Id private UUID id;
    @Column(name = "user_id", nullable = false, unique = true) private UUID userId;
    @Column(nullable = false, length = 12) private String environment;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "last_verified_at", nullable = false) private Instant lastVerifiedAt;

    protected PayPalConnection() {}

    PayPalConnection(UUID userId) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.environment = "SANDBOX";
        this.createdAt = Instant.now();
        this.lastVerifiedAt = this.createdAt;
    }

    public void verifiedNow() { this.lastVerifiedAt = Instant.now(); }
    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastVerifiedAt() { return lastVerifiedAt; }
}
