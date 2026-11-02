package ai.finsight.recurring;

import ai.finsight.transaction.*;import ai.finsight.user.UserRepository;
import jakarta.validation.constraints.*;import org.springframework.http.HttpStatus;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;import org.springframework.web.server.ResponseStatusException;
import java.math.*;import java.time.*;import java.util.*;

@Service @Transactional
public class RecurringTransactionService {
 public record Input(@NotBlank @Size(max=120) String title,@NotNull RecurringTransaction.Type type,@NotNull @DecimalMin("0.01") @Digits(integer=17,fraction=2) BigDecimal amount,@NotBlank @Size(max=60) String category,@NotNull RecurringTransaction.Frequency frequency,@NotNull LocalDate nextDueDate,LocalDate endDate,boolean active){}
 public record View(UUID id,String title,RecurringTransaction.Type type,BigDecimal amount,String category,RecurringTransaction.Frequency frequency,LocalDate nextDueDate,LocalDate endDate,boolean active){}
 public record ActiveInput(boolean active){}
 private final RecurringTransactionRepository recurring;private final RecurringOccurrenceRepository occurrences;private final TransactionRepository transactions;private final UserRepository users;
 public RecurringTransactionService(RecurringTransactionRepository r,RecurringOccurrenceRepository o,TransactionRepository t,UserRepository u){recurring=r;occurrences=o;transactions=t;users=u;}
 @Transactional(readOnly=true) public List<View> list(String email){UUID owner=owner(email);return recurring.findAllByUserIdOrderByNextDueDateAsc(owner).stream().map(this::view).toList();}
 @Transactional(readOnly=true) public List<View> upcoming(String email){LocalDate today=LocalDate.now(),limit=today.plusDays(30);return recurring.findAllByUserIdOrderByNextDueDateAsc(owner(email)).stream().filter(RecurringTransaction::isActive).filter(r->!r.getNextDueDate().isAfter(limit)&& (r.getEndDate()==null||!r.getNextDueDate().isAfter(r.getEndDate()))).toList().stream().map(this::view).toList();}
 public View create(String email,Input in){UUID owner=owner(email);validate(in);return view(recurring.save(new RecurringTransaction(owner,in.title().trim(),in.type(),in.amount(),in.category().trim(),in.frequency(),in.nextDueDate(),in.endDate())));}
 public View update(String email,UUID id,Input in){UUID owner=owner(email);validate(in);RecurringTransaction r=recurring.findByIdAndUserId(id,owner).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));r.update(in.title().trim(),in.type(),in.amount(),in.category().trim(),in.frequency(),in.nextDueDate(),in.endDate(),in.active());return view(recurring.save(r));}
 public View setActive(String email,UUID id,boolean active){RecurringTransaction r=find(email,id);r.setActive(active);return view(recurring.save(r));}
 public void delete(String email,UUID id){recurring.delete(find(email,id));}
 public UUID recordOccurrence(String email,UUID id,LocalDate date){UUID owner=owner(email);RecurringTransaction r=recurring.findByIdAndUserId(id,owner).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));if(occurrences.existsByRecurringTransactionIdAndOccurrenceDate(id,date))throw new ResponseStatusException(HttpStatus.CONFLICT,"This scheduled occurrence was already recorded.");if(!r.isActive())throw new ResponseStatusException(HttpStatus.CONFLICT,"Paused recurrence cannot be recorded.");if(!r.getNextDueDate().equals(date)||date.isAfter(LocalDate.now())||(r.getEndDate()!=null&&date.isAfter(r.getEndDate())))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Only a due occurrence can be recorded.");FinancialTransaction.Direction direction=r.getType()==RecurringTransaction.Type.INCOME?FinancialTransaction.Direction.INCOME:FinancialTransaction.Direction.EXPENSE;FinancialTransaction transaction=transactions.save(new FinancialTransaction(owner,r.getTitle(),r.getCategory(),r.getAmount(),direction,date));occurrences.save(new RecurringOccurrence(owner,id,date,transaction.getId()));LocalDate next=advance(date,r.getFrequency());r.setNextDueDate(next);if(r.getEndDate()!=null&&next.isAfter(r.getEndDate()))r.setActive(false);recurring.save(r);return transaction.getId();}
 private void validate(Input in){if(in.endDate()!=null&&in.endDate().isBefore(in.nextDueDate()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"End date must not be earlier than the next due date.");}
 private LocalDate advance(LocalDate date,RecurringTransaction.Frequency frequency){return switch(frequency){case DAILY->date.plusDays(1);case WEEKLY->date.plusWeeks(1);case MONTHLY->date.plusMonths(1);case YEARLY->date.plusYears(1);};}
 private View view(RecurringTransaction r){return new View(r.getId(),r.getTitle(),r.getType(),r.getAmount(),r.getCategory(),r.getFrequency(),r.getNextDueDate(),r.getEndDate(),r.isActive());}
 private RecurringTransaction find(String email,UUID id){return recurring.findByIdAndUserId(id,owner(email)).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));}
 private UUID owner(String email){return users.findByEmailIgnoreCase(email).orElseThrow().getId();}
}
