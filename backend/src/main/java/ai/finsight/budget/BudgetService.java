package ai.finsight.budget;

import ai.finsight.transaction.*;
import ai.finsight.user.UserRepository;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.*;
import java.time.*;
import java.util.*;

@Service
@Transactional
public class BudgetService {
 public record Input(@NotBlank @Size(max=120) String name,@NotBlank @Size(max=60) String category,@NotNull @DecimalMin(value="0.01") @Digits(integer=17,fraction=2) BigDecimal monthlyLimit,@NotNull LocalDate startDate,@NotNull LocalDate endDate,@NotNull @DecimalMin(value="1.00") @DecimalMax(value="100.00") @Digits(integer=3,fraction=2) BigDecimal alertThresholdPercentage){}
 public record View(UUID id,String name,String category,BigDecimal monthlyLimit,LocalDate startDate,LocalDate endDate,BigDecimal alertThresholdPercentage,BigDecimal amountSpent,BigDecimal remainingAmount,BigDecimal percentageConsumed,boolean alertTriggered,boolean exceeded,String status){}
 private final BudgetRepository budgets; private final UserRepository users; private final TransactionRepository transactions;
 public BudgetService(BudgetRepository b,UserRepository u,TransactionRepository t){budgets=b;users=u;transactions=t;}
 @Transactional(readOnly=true) public List<View> list(String email){UUID owner=owner(email);LocalDate from=LocalDate.now().withDayOfMonth(1),to=from.plusMonths(1);Map<String,BigDecimal> spent=new HashMap<>();transactions.categoryTotals(owner,from,to,FinancialTransaction.Direction.EXPENSE).forEach(r->spent.put((String)r[0],(BigDecimal)r[1]));return budgets.findAllByUserIdOrderByStartDateDesc(owner).stream().map(b->view(b,owner,spent.getOrDefault(b.getCategory(),BigDecimal.ZERO))).toList();}
 public View create(String email,Input in){UUID owner=owner(email);validatePeriod(in.startDate(),in.endDate());String category=in.category().trim();if(budgets.existsOverlapping(owner,category,in.startDate(),in.endDate(),null))throw new ResponseStatusException(HttpStatus.CONFLICT,"An overlapping budget exists for this category.");Budget b=budgets.save(new Budget(owner,in.name().trim(),category,in.monthlyLimit(),in.startDate(),in.endDate(),in.alertThresholdPercentage()));return view(b,owner,spentThisMonth(owner,category));}
 public View update(String email,UUID id,Input in){UUID owner=owner(email);validatePeriod(in.startDate(),in.endDate());Budget b=budgets.findByIdAndUserId(id,owner).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));String category=in.category().trim();if(budgets.existsOverlapping(owner,category,in.startDate(),in.endDate(),id))throw new ResponseStatusException(HttpStatus.CONFLICT,"An overlapping budget exists for this category.");b.update(in.name().trim(),category,in.monthlyLimit(),in.startDate(),in.endDate(),in.alertThresholdPercentage());return view(budgets.save(b),owner,spentThisMonth(owner,category));}
 public void delete(String email,UUID id){UUID owner=owner(email);Budget b=budgets.findByIdAndUserId(id,owner).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));budgets.delete(b);}
 private void validatePeriod(LocalDate start,LocalDate end){if(end.isBefore(start)||start.getDayOfMonth()!=1||!end.equals(YearMonth.from(end).atEndOfMonth()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Monthly budget dates must cover complete calendar months.");}
 private BigDecimal spentThisMonth(UUID owner,String category){LocalDate from=LocalDate.now().withDayOfMonth(1);return transactions.sumForOwnerCategoryPeriod(owner,from,from.plusMonths(1),FinancialTransaction.Direction.EXPENSE,category);}
 private View view(Budget b,UUID owner,BigDecimal rawSpent){LocalDate now=LocalDate.now();boolean active=!now.isBefore(b.getStartDate())&&!now.isAfter(b.getEndDate());BigDecimal spent=active?money(rawSpent):BigDecimal.ZERO.setScale(2);BigDecimal remaining=b.getMonthlyLimit().subtract(spent).setScale(2,RoundingMode.HALF_UP);BigDecimal percent=spent.multiply(BigDecimal.valueOf(100)).divide(b.getMonthlyLimit(),2,RoundingMode.HALF_UP);boolean exceeded=spent.compareTo(b.getMonthlyLimit())>0;boolean alert=percent.compareTo(b.getAlertThresholdPercentage())>=0;String status=exceeded?"EXCEEDED":alert?"ALERT":active?"ON_TRACK":now.isBefore(b.getStartDate())?"UPCOMING":"ENDED";return new View(b.getId(),b.getName(),b.getCategory(),money(b.getMonthlyLimit()),b.getStartDate(),b.getEndDate(),b.getAlertThresholdPercentage(),spent,remaining,percent,alert,exceeded,status);}
 private BigDecimal money(BigDecimal v){return v.setScale(2,RoundingMode.HALF_UP);} private UUID owner(String email){return users.findByEmailIgnoreCase(email).orElseThrow().getId();}
}
