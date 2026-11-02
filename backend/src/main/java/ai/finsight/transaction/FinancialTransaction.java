package ai.finsight.transaction;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
@Entity @Table(name="financial_transaction",indexes=@Index(name="transaction_user_date_idx",columnList="user_id,occurred_on"))
public class FinancialTransaction {
 @Id private UUID id; @Column(name="user_id",nullable=false) private UUID userId; @Column(nullable=false,length=120) private String description; @Column(nullable=false,length=60) private String category; @Column(nullable=false,precision=19,scale=2) private BigDecimal amount; @Enumerated(EnumType.STRING) @Column(nullable=false,length=10) private Direction direction; @Column(name="occurred_on",nullable=false) private LocalDate occurredOn; @Column(name="created_at",nullable=false) private Instant createdAt;
 @Column(name="source_provider",length=20) private String sourceProvider; @Column(name="external_reference",length=64) private String externalReference; @Column(name="currency_code",nullable=false,length=3) private String currencyCode="INR";
 protected FinancialTransaction(){}
 public FinancialTransaction(UUID userId,String description,String category,BigDecimal amount,Direction direction,LocalDate occurredOn){this.id=UUID.randomUUID();this.userId=userId;this.description=description;this.category=category;this.amount=amount;this.direction=direction;this.occurredOn=occurredOn;this.createdAt=Instant.now();}
 public FinancialTransaction(UUID userId,String description,String category,BigDecimal amount,Direction direction,LocalDate occurredOn,String sourceProvider,String externalReference,String currencyCode){this(userId,description,category,amount,direction,occurredOn);this.sourceProvider=sourceProvider;this.externalReference=externalReference;this.currencyCode=currencyCode;}
 public void update(String description,String category,BigDecimal amount,Direction direction,LocalDate occurredOn){this.description=description;this.category=category;this.amount=amount;this.direction=direction;this.occurredOn=occurredOn;}
 public UUID getId(){return id;} public UUID getUserId(){return userId;} public String getDescription(){return description;} public String getCategory(){return category;} public BigDecimal getAmount(){return amount;} public Direction getDirection(){return direction;} public LocalDate getOccurredOn(){return occurredOn;}
 public String getSourceProvider(){return sourceProvider;} public String getExternalReference(){return externalReference;} public String getCurrencyCode(){return currencyCode;}
 public enum Direction { INCOME, EXPENSE }
}
