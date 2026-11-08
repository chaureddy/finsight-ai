package ai.finsight.account;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserAccountConnectionRepository extends JpaRepository<UserAccountConnection, UUID> {
    List<UserAccountConnection> findAllByUserIdOrderByCreatedAtDesc(UUID userId);
    Optional<UserAccountConnection> findByIdAndUserId(UUID id, UUID userId);
    Optional<UserAccountConnection> findByUserIdAndProviderAndAccountName(UUID userId, String provider, String accountName);
    Optional<UserAccountConnection> findByUserIdAndProvider(UUID userId, String provider);
    boolean existsByUserIdAndProviderAndStatus(UUID userId, String provider, String status);
    void deleteByUserIdAndProvider(UUID userId, String provider);
}

