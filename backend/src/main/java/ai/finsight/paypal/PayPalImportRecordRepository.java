package ai.finsight.paypal;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface PayPalImportRecordRepository extends JpaRepository<PayPalImportRecord, UUID> {
    long countByConnection_IdAndState(UUID connectionId, PayPalImportRecord.State state);
    boolean existsByConnection_IdAndSourceFingerprint(UUID connectionId, String sourceFingerprint);
    java.util.List<PayPalImportRecord> findAllByConnection_IdAndStateOrderByCreatedAtDesc(UUID connectionId, PayPalImportRecord.State state);
}
