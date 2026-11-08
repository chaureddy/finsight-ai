package ai.finsight.account;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_account_connection", uniqueConstraints = @UniqueConstraint(name = "user_account_provider_uq", columnNames = {"user_id", "provider", "account_name"}))
public class UserAccountConnection {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 32)
    private String provider;

    @Column(name = "account_name", nullable = false, length = 120)
    private String accountName;

    @Column(name = "account_identifier", length = 120)
    private String accountIdentifier;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "encrypted_credentials", columnDefinition = "TEXT")
    private String encryptedCredentials;

    @Column(columnDefinition = "TEXT")
    private String metadata;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserAccountConnection() {}

    public UserAccountConnection(UUID userId, String provider, String accountName, String accountIdentifier, String status, String encryptedCredentials, String metadata) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.provider = provider;
        this.accountName = accountName;
        this.accountIdentifier = accountIdentifier;
        this.status = status;
        this.encryptedCredentials = encryptedCredentials;
        this.metadata = metadata;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void updateStatus(String status, String metadata) {
        this.status = status;
        this.metadata = metadata;
        this.updatedAt = Instant.now();
    }

    public void updateCredentials(String encryptedCredentials) {
        this.encryptedCredentials = encryptedCredentials;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getProvider() { return provider; }
    public String getAccountName() { return accountName; }
    public String getAccountIdentifier() { return accountIdentifier; }
    public String getStatus() { return status; }
    public String getEncryptedCredentials() { return encryptedCredentials; }
    public String getMetadata() { return metadata; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

