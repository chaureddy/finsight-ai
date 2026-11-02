package ai.finsight.recurring;
import jakarta.persistence.*;import java.math.BigDecimal;import java.time.*;import java.util.UUID;
@Entity @Table(name="recurring_transaction")
public class RecurringTransaction {
 @Id private UUID id;@Column(name="user_id",nullable=false) private UUID userId;@Column(nullable=false,length=120) private String title;@Enumerated(EnumType.STRING)@Column(name="transaction_type",nullable=false,length=10)private Type type;@Column(nullable=false,precision=19,scale=2)private BigDecimal amount;@Column(nullable=false,length=60)private String category;@Enumerated(EnumType.STRING)@Column(nullable=false,length=10)private Frequency frequency;@Column(name="next_due_date",nullable=false)private LocalDate nextDueDate;@Column(name="end_date")private LocalDate endDate;@Column(nullable=false)private boolean active;@Column(name="created_at",nullable=false)private Instant createdAt;@Column(name="updated_at",nullable=false)private Instant updatedAt;
 protected RecurringTransaction(){}
 public RecurringTransaction(UUID user,String title,Type type,BigDecimal amount,String category,Frequency frequency,LocalDate next,LocalDate end){id=UUID.randomUUID();userId=user;this.title=title;this.type=type;this.amount=amount;this.category=category;this.frequency=frequency;nextDueDate=next;endDate=end;active=true;createdAt=Instant.now();updatedAt=createdAt;}
 public void update(String title,Type type,BigDecimal amount,String category,Frequency frequency,LocalDate next,LocalDate end,boolean active){this.title=title;this.type=type;this.amount=amount;this.category=category;this.frequency=frequency;nextDueDate=next;endDate=end;this.active=active;updatedAt=Instant.now();}
 public void setActive(boolean value){active=value;updatedAt=Instant.now();}public void setNextDueDate(LocalDate value){nextDueDate=value;updatedAt=Instant.now();}
 public UUID getId(){return id;}public UUID getUserId(){return userId;}public String getTitle(){return title;}public Type getType(){return type;}public BigDecimal getAmount(){return amount;}public String getCategory(){return category;}public Frequency getFrequency(){return frequency;}public LocalDate getNextDueDate(){return nextDueDate;}public LocalDate getEndDate(){return endDate;}public boolean isActive(){return active;}
 public enum Type{INCOME,EXPENSE}public enum Frequency{DAILY,WEEKLY,MONTHLY,YEARLY}
}
