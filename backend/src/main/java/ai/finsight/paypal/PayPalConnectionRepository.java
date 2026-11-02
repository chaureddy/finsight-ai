package ai.finsight.paypal;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface PayPalConnectionRepository extends JpaRepository<PayPalConnection, UUID> {
    Optional<PayPalConnection> findByUserId(UUID userId);
    Optional<PayPalConnection> findByEnvironment(String environment);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from PayPalConnection c where c.environment=:environment")
    Optional<PayPalConnection> lockByEnvironment(@Param("environment") String environment);
}
