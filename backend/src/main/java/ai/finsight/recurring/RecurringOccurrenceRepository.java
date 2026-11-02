package ai.finsight.recurring;
import org.springframework.data.jpa.repository.JpaRepository;import java.time.LocalDate;import java.util.Optional;import java.util.UUID;
public interface RecurringOccurrenceRepository extends JpaRepository<RecurringOccurrence,UUID>{boolean existsByRecurringTransactionIdAndOccurrenceDate(UUID recurringTransactionId,LocalDate occurrenceDate);Optional<RecurringOccurrence> findByRecurringTransactionIdAndOccurrenceDate(UUID recurringTransactionId,LocalDate occurrenceDate);}
