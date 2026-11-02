package ai.finsight.budget;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.*;

public interface BudgetRepository extends JpaRepository<Budget,UUID> {
 List<Budget> findAllByUserIdOrderByStartDateDesc(UUID userId);
 Optional<Budget> findByIdAndUserId(UUID id,UUID userId);
 @Query("select (count(b)>0) from Budget b where b.userId=:user and b.category=:category and b.startDate<=:end and b.endDate>=:start and (:excludeId is null or b.id<>:excludeId)")
 boolean existsOverlapping(@Param("user") UUID user,@Param("category") String category,@Param("start") LocalDate start,@Param("end") LocalDate end,@Param("excludeId") UUID excludeId);
}
