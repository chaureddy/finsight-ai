package ai.finsight.paypal;

import ai.finsight.user.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@RestController
@RequestMapping("/api/integrations/paypal")
public class PayPalController {
    private final UserRepository users;
    private final PayPalGateway gateway;
    private final PayPalApiClient apiClient;
    private final PayPalImportPersistenceService persistence;

    public PayPalController(UserRepository users, PayPalGateway gateway, PayPalApiClient apiClient, PayPalImportPersistenceService persistence) {
        this.users = users; this.gateway = gateway; this.apiClient = apiClient; this.persistence = persistence;
    }

    public record ImportRequest(@NotNull LocalDate startDate, @NotNull LocalDate endDate) {}
    public record ImportResponse(int importedCount, int duplicateCount, int reviewCount, String environment, String state) {}

    private UUID owner(Authentication auth) { return users.findByEmailIgnoreCase(auth.getName()).orElseThrow().getId(); }

    @GetMapping("/status")
    public PayPalImportPersistenceService.ConnectionStatus status(Authentication auth) {
        return persistence.status(owner(auth), apiClient.isConfigured());
    }

    @GetMapping("/review")
    public java.util.List<PayPalImportPersistenceService.ReviewItem> reviews(Authentication auth) {
        return persistence.reviews(owner(auth));
    }

    @PostMapping("/import")
    @ResponseStatus(HttpStatus.OK)
    public ImportResponse importTransactions(@Valid @RequestBody ImportRequest request, Authentication auth) {
        LocalDate today = LocalDate.now();
        if (request.startDate().isAfter(request.endDate()) || request.endDate().isAfter(today)
                || ChronoUnit.DAYS.between(request.startDate(), request.endDate()) > 30
                || request.startDate().isBefore(today.minusYears(3))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a valid date range of at most 31 days within PayPal's available history.");
        }
        UUID ownerId = owner(auth);
        persistence.assertCanConnect(ownerId);
        var source = gateway.search(request.startDate(), request.endDate());
        var counts = persistence.persist(ownerId, source);
        return new ImportResponse(counts.importedCount(), counts.duplicateCount(), counts.reviewCount(), "SANDBOX", "CONNECTED");
    }
}
