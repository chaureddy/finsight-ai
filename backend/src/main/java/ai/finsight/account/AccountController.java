package ai.finsight.account;

import ai.finsight.paypal.PayPalApiClient;
import ai.finsight.paypal.PayPalImportPersistenceService;
import ai.finsight.transaction.TransactionRepository;
import ai.finsight.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.*;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {
    private final UserRepository users;
    private final UserAccountConnectionRepository accountRepo;
    private final PayPalImportPersistenceService paypalService;
    private final PayPalApiClient paypalClient;
    private final TransactionRepository transactionRepo;
    private final NamedParameterJdbcTemplate jdbc;

    public AccountController(UserRepository users,
                             UserAccountConnectionRepository accountRepo,
                             PayPalImportPersistenceService paypalService,
                             PayPalApiClient paypalClient,
                             TransactionRepository transactionRepo,
                             NamedParameterJdbcTemplate jdbc) {
        this.users = users;
        this.accountRepo = accountRepo;
        this.paypalService = paypalService;
        this.paypalClient = paypalClient;
        this.transactionRepo = transactionRepo;
        this.jdbc = jdbc;
    }

    public record ConnectedAccountView(String id, String provider, String accountName, String accountType, String status, String lastSync, long transactionCount, BigDecimal balance) {}
    public record AccountsOverview(List<ConnectedAccountView> accounts, int totalConnected, long totalTransactionsImported) {}

    private UUID owner(Authentication auth) {
        return users.findByEmailIgnoreCase(auth.getName()).orElseThrow().getId();
    }

    @GetMapping
    public AccountsOverview listAccounts(Authentication auth) {
        UUID ownerId = owner(auth);
        List<ConnectedAccountView> list = new ArrayList<>();

        // 1. PayPal Sandbox Account
        var ppStatus = paypalService.status(ownerId, paypalClient.isConfigured());
        long ppTxCount = transactionRepo.countByUserIdAndSourceProvider(ownerId, "PAYPAL");
        String ppLastSync = ppStatus.lastVerifiedAt() != null ? ppStatus.lastVerifiedAt().toString().substring(0, 10) : null;
        list.add(new ConnectedAccountView(
                "paypal-sandbox",
                "PAYPAL",
                "PayPal Sandbox Reporting",
                "Digital Wallet / Payment Gateway",
                ppStatus.connected() ? "CONNECTED" : ppStatus.configured() ? "READY" : "NOT_CONFIGURED",
                ppLastSync,
                ppTxCount,
                null
        ));

        // 2. CSV Statements and Custom Connections
        List<UserAccountConnection> userConns = accountRepo.findAllByUserIdOrderByCreatedAtDesc(ownerId);
        for (UserAccountConnection uac : userConns) {
            long count = transactionRepo.countByUserIdAndSourceProvider(ownerId, "CSV_IMPORT");
            list.add(new ConnectedAccountView(
                    uac.getId().toString(),
                    uac.getProvider(),
                    uac.getAccountName(),
                    "Statement Import",
                    uac.getStatus(),
                    uac.getUpdatedAt().toString().substring(0, 10),
                    count,
                    null
            ));
        }

        // 3. User-entered Cash & Bank Assets
        List<Map<String, Object>> assets = jdbc.queryForList(
                "SELECT id, name, category, current_value, updated_at FROM financial_asset WHERE user_id=:user AND category IN ('Cash', 'Bank Account')",
                new MapSqlParameterSource("user", ownerId)
        );
        for (Map<String, Object> a : assets) {
            BigDecimal val = (BigDecimal) a.get("current_value");
            list.add(new ConnectedAccountView(
                    a.get("id").toString(),
                    "MANUAL_BANK",
                    (String) a.get("name"),
                    (String) a.get("category"),
                    "ACTIVE",
                    a.get("updated_at") != null ? a.get("updated_at").toString().substring(0, 10) : null,
                    0,
                    val
            ));
        }

        int totalConnected = (int) list.stream().filter(a -> "CONNECTED".equals(a.status()) || "ACTIVE".equals(a.status())).count();
        long totalImported = ppTxCount + transactionRepo.countByUserIdAndSourceProvider(ownerId, "CSV_IMPORT");
        return new AccountsOverview(list, totalConnected, totalImported);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAccount(@PathVariable String id, Authentication auth) {
        UUID ownerId = owner(auth);
        if ("paypal-sandbox".equals(id)) {
            paypalService.disconnect(ownerId);
            return;
        }
        try {
            UUID connId = UUID.fromString(id);
            accountRepo.findByIdAndUserId(connId, ownerId).ifPresent(accountRepo::delete);
        } catch (IllegalArgumentException ignored) {}
    }
}

