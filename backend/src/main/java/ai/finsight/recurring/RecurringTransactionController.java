package ai.finsight.recurring;
import jakarta.validation.Valid;import jakarta.validation.constraints.NotNull;import org.springframework.http.HttpStatus;import org.springframework.security.core.Authentication;import org.springframework.web.bind.annotation.*;import java.time.LocalDate;import java.util.*;
@RestController @RequestMapping("/api/recurring-transactions")
public class RecurringTransactionController {
 public record OccurrenceInput(@NotNull LocalDate occurrenceDate){}public record OccurrenceResult(UUID transactionId,String notice){}
 private final RecurringTransactionService service;public RecurringTransactionController(RecurringTransactionService s){service=s;}
 @GetMapping public List<RecurringTransactionService.View> list(Authentication a){return service.list(a.getName());}
 @GetMapping("/upcoming") public List<RecurringTransactionService.View> upcoming(Authentication a){return service.upcoming(a.getName());}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) public RecurringTransactionService.View create(@Valid @RequestBody RecurringTransactionService.Input i,Authentication a){return service.create(a.getName(),i);}
 @PutMapping("/{id}") public RecurringTransactionService.View update(@PathVariable UUID id,@Valid @RequestBody RecurringTransactionService.Input i,Authentication a){return service.update(a.getName(),id,i);}
 @PatchMapping("/{id}/active") public RecurringTransactionService.View setActive(@PathVariable UUID id,@Valid @RequestBody RecurringTransactionService.ActiveInput i,Authentication a){return service.setActive(a.getName(),id,i.active());}
 @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable UUID id,Authentication a){service.delete(a.getName(),id);}
 @PostMapping("/{id}/occurrences") @ResponseStatus(HttpStatus.CREATED) public OccurrenceResult recordOccurrence(@PathVariable UUID id,@Valid @RequestBody OccurrenceInput i,Authentication a){return new OccurrenceResult(service.recordOccurrence(a.getName(),id,i.occurrenceDate()),"Created only after explicit confirmation; no money is moved.");}
}
