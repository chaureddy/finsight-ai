package ai.finsight.budget;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/budgets")
public class BudgetController {
 private final BudgetService service;
 public BudgetController(BudgetService service){this.service=service;}
 @GetMapping public List<BudgetService.View> list(Authentication auth){return service.list(auth.getName());}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) public BudgetService.View create(@Valid @RequestBody BudgetService.Input input,Authentication auth){return service.create(auth.getName(),input);}
 @PutMapping("/{id}") public BudgetService.View update(@PathVariable UUID id,@Valid @RequestBody BudgetService.Input input,Authentication auth){return service.update(auth.getName(),id,input);}
 @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable UUID id,Authentication auth){service.delete(auth.getName(),id);}
}
