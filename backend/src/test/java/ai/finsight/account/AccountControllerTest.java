package ai.finsight.account;

import ai.finsight.paypal.PayPalApiClient;
import ai.finsight.paypal.PayPalImportPersistenceService;
import ai.finsight.transaction.TransactionRepository;
import ai.finsight.user.AppUser;
import ai.finsight.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AccountControllerTest {
    private UserRepository users;
    private UserAccountConnectionRepository accountRepo;
    private PayPalImportPersistenceService paypalService;
    private PayPalApiClient paypalClient;
    private TransactionRepository transactionRepo;
    private NamedParameterJdbcTemplate jdbc;

    private AccountController controller;
    private Authentication auth;
    private final String email = "user@example.com";
    private final UUID ownerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        accountRepo = mock(UserAccountConnectionRepository.class);
        paypalService = mock(PayPalImportPersistenceService.class);
        paypalClient = mock(PayPalApiClient.class);
        transactionRepo = mock(TransactionRepository.class);
        jdbc = mock(NamedParameterJdbcTemplate.class);

        controller = new AccountController(users, accountRepo, paypalService, paypalClient, transactionRepo, jdbc);

        AppUser user = new AppUser(email, "pw", "Alex");
        when(users.findByEmailIgnoreCase(email)).thenReturn(Optional.of(user));
        auth = UsernamePasswordAuthenticationToken.authenticated(email, "", List.of());

        when(paypalService.status(any(), anyBoolean())).thenReturn(
                new PayPalImportPersistenceService.ConnectionStatus(true, true, "CONNECTED", Instant.now(), Instant.now())
        );
        when(paypalClient.isConfigured()).thenReturn(true);
        when(accountRepo.findAllByUserIdOrderByCreatedAtDesc(any())).thenReturn(List.of(
                new UserAccountConnection(ownerId, "CSV_STATEMENT", "HDFC Bank Statement", "CSV", "CONNECTED", null, "{}")
        ));
        when(jdbc.queryForList(anyString(), any(org.springframework.jdbc.core.namedparam.SqlParameterSource.class))).thenReturn(List.of(
                Map.of("id", UUID.randomUUID(), "name", "Savings Account", "category", "Bank Account", "current_value", new java.math.BigDecimal("50000.00"), "updated_at", Instant.now())
        ));
    }

    @Test
    void listsConnectedAccountsConsolidated() {
        var overview = controller.listAccounts(auth);
        assertNotNull(overview);
        assertEquals(3, overview.accounts().size()); // PayPal, CSV, Manual Bank
        assertTrue(overview.accounts().stream().anyMatch(a -> a.provider().equals("PAYPAL")));
        assertTrue(overview.accounts().stream().anyMatch(a -> a.provider().equals("CSV_STATEMENT")));
        assertTrue(overview.accounts().stream().anyMatch(a -> a.provider().equals("MANUAL_BANK")));
    }

    @Test
    void disconnectsPayPalAccount() {
        controller.deleteAccount("paypal-sandbox", auth);
        verify(paypalService, times(1)).disconnect(any());
    }
}

