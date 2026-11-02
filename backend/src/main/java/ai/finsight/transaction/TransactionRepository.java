package ai.finsight.transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
public interface TransactionRepository extends JpaRepository<FinancialTransaction,UUID> {
 List<FinancialTransaction> findAllByUserIdOrderByOccurredOnDesc(UUID userId);
 Optional<FinancialTransaction> findByIdAndUserId(UUID id,UUID userId);
 @Query("select coalesce(sum(t.amount), 0) from FinancialTransaction t where t.userId=:owner and t.occurredOn>=:from and t.occurredOn<:to and t.direction=:direction")
 BigDecimal sumForOwnerAndPeriod(@Param("owner") UUID owner,@Param("from") LocalDate from,@Param("to") LocalDate to,@Param("direction") FinancialTransaction.Direction direction);
 @Query("select coalesce(sum(t.amount),0) from FinancialTransaction t where t.userId=:owner and t.occurredOn>=:from and t.occurredOn<:to and t.direction=:direction and t.category=:category")
 BigDecimal sumForOwnerCategoryPeriod(@Param("owner") UUID owner,@Param("from") LocalDate from,@Param("to") LocalDate to,@Param("direction") FinancialTransaction.Direction direction,@Param("category") String category);
 @Query("select t.category, coalesce(sum(t.amount),0) from FinancialTransaction t where t.userId=:owner and t.occurredOn>=:from and t.occurredOn<:to and t.direction=:direction group by t.category order by sum(t.amount) desc")
 List<Object[]> categoryTotals(@Param("owner") UUID owner,@Param("from") LocalDate from,@Param("to") LocalDate to,@Param("direction") FinancialTransaction.Direction direction);
}
