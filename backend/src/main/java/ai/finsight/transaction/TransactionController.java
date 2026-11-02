package ai.finsight.transaction;
import ai.finsight.user.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
@RestController @RequestMapping("/api/transactions")
public class TransactionController {
 private final TransactionRepository transactions; private final UserRepository users;
 public TransactionController(TransactionRepository t,UserRepository u){transactions=t;users=u;}
 public record Input(@NotBlank @Size(max=120) String description,@NotBlank @Size(max=60) String category,@NotNull @DecimalMin("0.01") @Digits(integer=17,fraction=2) BigDecimal amount,@NotNull FinancialTransaction.Direction direction,@NotNull LocalDate occurredOn){}
 private UUID owner(Authentication a){return users.findByEmailIgnoreCase(a.getName()).orElseThrow().getId();}
 @GetMapping public List<FinancialTransaction> list(Authentication a){return transactions.findAllByUserIdOrderByOccurredOnDesc(owner(a));}
 @PostMapping public ResponseEntity<FinancialTransaction> create(@Valid @RequestBody Input input,Authentication a){return ResponseEntity.status(HttpStatus.CREATED).body(transactions.save(new FinancialTransaction(owner(a),input.description().trim(),input.category().trim(),input.amount(),input.direction(),input.occurredOn())));}
 @PutMapping("/{id}") public FinancialTransaction update(@PathVariable UUID id,@Valid @RequestBody Input input,Authentication a){FinancialTransaction t=transactions.findByIdAndUserId(id,owner(a)).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));t.update(input.description().trim(),input.category().trim(),input.amount(),input.direction(),input.occurredOn());return transactions.save(t);}
 @DeleteMapping("/{id}") public ResponseEntity<Void> delete(@PathVariable UUID id,Authentication a){FinancialTransaction t=transactions.findByIdAndUserId(id,owner(a)).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));transactions.delete(t);return ResponseEntity.noContent().build();}
}
