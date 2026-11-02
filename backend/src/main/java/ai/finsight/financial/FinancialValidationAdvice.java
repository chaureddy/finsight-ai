package ai.finsight.financial;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ai.finsight.budget.BudgetController;
import ai.finsight.goal.FinancialGoalController;
import ai.finsight.recurring.RecurringTransactionController;

import java.util.Map;
import java.util.TreeSet;

/** Returns field names only; rejected financial values are intentionally never logged or echoed. */
@RestControllerAdvice(assignableTypes = {FinancialController.class,BudgetController.class,FinancialGoalController.class,RecurringTransactionController.class})
public class FinancialValidationAdvice {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String,Object>> invalidFinancialInput(MethodArgumentNotValidException exception) {
        TreeSet<String> invalidFields=new TreeSet<>();
        exception.getBindingResult().getFieldErrors().forEach(error->invalidFields.add(error.getField()));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error","Invalid financial record","invalidFields",invalidFields));
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String,String>> unreadableFinancialInput(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest().body(Map.of("error","Invalid financial request format or date."));
    }
}
