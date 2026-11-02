package ai.finsight.goal;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface FinancialGoalRepository extends JpaRepository<FinancialGoal,UUID>{List<FinancialGoal> findAllByUserIdOrderByTargetDateAsc(UUID userId);Optional<FinancialGoal> findByIdAndUserId(UUID id,UUID userId);}
