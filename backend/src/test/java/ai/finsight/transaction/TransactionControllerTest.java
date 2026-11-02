package ai.finsight.transaction;
import ai.finsight.user.AppUser;
import ai.finsight.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
class TransactionControllerTest {
 private TransactionRepository transactions; private UserRepository users; private TransactionController controller; private Authentication auth; private UUID ownerId;
 @BeforeEach void setUp(){transactions=mock(TransactionRepository.class);users=mock(UserRepository.class);controller=new TransactionController(transactions,users);auth=UsernamePasswordAuthenticationToken.authenticated("owner@example.com","",List.of());AppUser owner=new AppUser("owner@example.com","hash","Owner");ownerId=owner.getId();when(users.findByEmailIgnoreCase("owner@example.com")).thenReturn(Optional.of(owner));}
 @Test void listsOnlyRecordsQueriedForAuthenticatedOwner(){when(transactions.findAllByUserIdOrderByOccurredOnDesc(ownerId)).thenReturn(List.of());assertTrue(controller.list(auth).isEmpty());verify(transactions).findAllByUserIdOrderByOccurredOnDesc(ownerId);}
 @Test void createsTransactionWithAuthenticatedOwner(){var input=new TransactionController.Input("Lunch","Food",new BigDecimal("12.50"),FinancialTransaction.Direction.EXPENSE,LocalDate.now());when(transactions.save(any())).thenAnswer(call->call.getArgument(0));var response=controller.create(input,auth);assertEquals(201,response.getStatusCode().value());assertEquals(ownerId,response.getBody().getUserId());}
 @Test void updateForAnotherOwnersTransactionIsNotFound(){UUID foreignId=UUID.randomUUID();when(transactions.findByIdAndUserId(foreignId,ownerId)).thenReturn(Optional.empty());var input=new TransactionController.Input("Changed","Food",new BigDecimal("25.00"),FinancialTransaction.Direction.EXPENSE,LocalDate.now());assertEquals(404,assertThrows(ResponseStatusException.class,()->controller.update(foreignId,input,auth)).getStatusCode().value());}
 @Test void deleteForAnotherOwnersTransactionIsNotFound(){UUID foreignId=UUID.randomUUID();when(transactions.findByIdAndUserId(foreignId,ownerId)).thenReturn(Optional.empty());assertEquals(404,assertThrows(ResponseStatusException.class,()->controller.delete(foreignId,auth)).getStatusCode().value());verify(transactions,never()).delete(any(FinancialTransaction.class));}
}
