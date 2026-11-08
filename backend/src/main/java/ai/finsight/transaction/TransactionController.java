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
 private final TransactionRepository transactions; private final UserRepository users; private final CsvTransactionService csvService;
 public TransactionController(TransactionRepository t,UserRepository u){this(t,u,null);}
 @org.springframework.beans.factory.annotation.Autowired
 public TransactionController(TransactionRepository t,UserRepository u,CsvTransactionService c){transactions=t;users=u;csvService=c;}
 public record Input(@NotBlank @Size(max=120) String description,@NotBlank @Size(max=60) String category,@NotNull @DecimalMin("0.01") @Digits(integer=17,fraction=2) BigDecimal amount,@NotNull FinancialTransaction.Direction direction,@NotNull LocalDate occurredOn){}
 public record CsvImportRequest(@NotBlank String csvData, String accountLabel){}
 private UUID owner(Authentication a){return users.findByEmailIgnoreCase(a.getName()).orElseThrow().getId();}
 @GetMapping public List<FinancialTransaction> list(Authentication a){return transactions.findAllByUserIdOrderByOccurredOnDesc(owner(a));}
 @PostMapping public ResponseEntity<FinancialTransaction> create(@Valid @RequestBody Input input,Authentication a){return ResponseEntity.status(HttpStatus.CREATED).body(transactions.save(new FinancialTransaction(owner(a),input.description().trim(),input.category().trim(),input.amount(),input.direction(),input.occurredOn())));}
 @PutMapping("/{id}") public FinancialTransaction update(@PathVariable UUID id,@Valid @RequestBody Input input,Authentication a){FinancialTransaction t=transactions.findByIdAndUserId(id,owner(a)).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));t.update(input.description().trim(),input.category().trim(),input.amount(),input.direction(),input.occurredOn());return transactions.save(t);}
 @DeleteMapping("/{id}") public ResponseEntity<Void> delete(@PathVariable UUID id,Authentication a){FinancialTransaction t=transactions.findByIdAndUserId(id,owner(a)).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));transactions.delete(t);return ResponseEntity.noContent().build();}
 @PostMapping("/import-csv") public CsvTransactionService.ImportSummary importCsv(@Valid @RequestBody CsvImportRequest request, Authentication a){return csvService.importCsv(owner(a),request.csvData(),request.accountLabel());}
 @GetMapping(value="/export", produces="text/csv") public ResponseEntity<String> exportCsv(Authentication a){String csv=csvService.exportCsv(owner(a));return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\"finsight-transactions.csv\"").body(csv);}
}
