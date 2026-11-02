package ai.finsight.recurring;
import org.springframework.data.jpa.repository.JpaRepository;import java.time.LocalDate;import java.util.*;
public interface RecurringTransactionRepository extends JpaRepository<RecurringTransaction,UUID>{List<RecurringTransaction> findAllByUserIdOrderByNextDueDateAsc(UUID userId);List<RecurringTransaction> findAllByUserIdAndActiveTrueAndTypeOrderByNextDueDateAsc(UUID userId,RecurringTransaction.Type type);Optional<RecurringTransaction> findByIdAndUserId(UUID id,UUID userId);}
