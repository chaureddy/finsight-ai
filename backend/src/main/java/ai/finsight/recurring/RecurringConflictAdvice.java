package ai.finsight.recurring;
import org.springframework.dao.DataIntegrityViolationException;import org.springframework.http.*;import org.springframework.web.bind.annotation.*;import java.util.Map;
@RestControllerAdvice(assignableTypes=RecurringTransactionController.class)
public class RecurringConflictAdvice{@ExceptionHandler(DataIntegrityViolationException.class)public ResponseEntity<Map<String,String>> duplicateOccurrence(DataIntegrityViolationException e){return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error","Scheduled occurrence has already been recorded."));}}
