package ai.finsight.budget;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

@Entity @Table(name="financial_budget")
public class Budget {
 @Id private UUID id;
 @Column(name="user_id",nullable=false) private UUID userId;
 @Column(nullable=false,length=120) private String name;
 @Column(nullable=false,length=60) private String category;
 @Column(name="monthly_limit",nullable=false,precision=19,scale=2) private BigDecimal monthlyLimit;
 @Column(name="start_date",nullable=false) private LocalDate startDate;
 @Column(name="end_date",nullable=false) private LocalDate endDate;
 @Column(name="alert_threshold_percentage",nullable=false,precision=5,scale=2) private BigDecimal alertThresholdPercentage;
 @Column(name="created_at",nullable=false) private Instant createdAt;
 @Column(name="updated_at",nullable=false) private Instant updatedAt;
 protected Budget(){}
 public Budget(UUID userId,String name,String category,BigDecimal monthlyLimit,LocalDate startDate,LocalDate endDate,BigDecimal alertThresholdPercentage){this.id=UUID.randomUUID();this.userId=userId;this.name=name;this.category=category;this.monthlyLimit=monthlyLimit;this.startDate=startDate;this.endDate=endDate;this.alertThresholdPercentage=alertThresholdPercentage;this.createdAt=Instant.now();this.updatedAt=this.createdAt;}
 public void update(String name,String category,BigDecimal limit,LocalDate start,LocalDate end,BigDecimal threshold){this.name=name;this.category=category;this.monthlyLimit=limit;this.startDate=start;this.endDate=end;this.alertThresholdPercentage=threshold;this.updatedAt=Instant.now();}
 public UUID getId(){return id;} public UUID getUserId(){return userId;} public String getName(){return name;} public String getCategory(){return category;} public BigDecimal getMonthlyLimit(){return monthlyLimit;} public LocalDate getStartDate(){return startDate;} public LocalDate getEndDate(){return endDate;} public BigDecimal getAlertThresholdPercentage(){return alertThresholdPercentage;}
}
