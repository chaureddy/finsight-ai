package ai.finsight.goal;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

@Entity @Table(name="financial_goal")
public class FinancialGoal {
 @Id private UUID id; @Column(name="user_id",nullable=false) private UUID userId;
 @Column(nullable=false,length=120) private String name; @Column(nullable=false,length=40) private String category;
 @Column(name="target_amount",nullable=false,precision=19,scale=2) private BigDecimal targetAmount;
 @Column(name="current_amount",nullable=false,precision=19,scale=2) private BigDecimal currentAmount;
 @Column(name="target_date",nullable=false) private LocalDate targetDate; @Enumerated(EnumType.STRING) @Column(nullable=false,length=10) private Priority priority;
 @Column(length=1000) private String description; @Column(name="created_at",nullable=false) private Instant createdAt; @Column(name="updated_at",nullable=false) private Instant updatedAt;
 protected FinancialGoal(){}
 public FinancialGoal(UUID user,String name,String category,BigDecimal target,BigDecimal current,LocalDate date,Priority priority,String description){id=UUID.randomUUID();userId=user;this.name=name;this.category=category;targetAmount=target;currentAmount=current;targetDate=date;this.priority=priority;this.description=description;createdAt=Instant.now();updatedAt=createdAt;}
 public void update(String name,String category,BigDecimal target,BigDecimal current,LocalDate date,Priority priority,String description){this.name=name;this.category=category;targetAmount=target;currentAmount=current;targetDate=date;this.priority=priority;this.description=description;updatedAt=Instant.now();}
 public UUID getId(){return id;} public UUID getUserId(){return userId;} public String getName(){return name;} public String getCategory(){return category;} public BigDecimal getTargetAmount(){return targetAmount;} public BigDecimal getCurrentAmount(){return currentAmount;} public LocalDate getTargetDate(){return targetDate;} public Priority getPriority(){return priority;} public String getDescription(){return description;}
 public enum Priority { LOW, MEDIUM, HIGH }
}
