package ai.finsight.paypal;

import ai.finsight.user.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
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
    public record ApproveReviewRequest(String description, String category, ai.finsight.transaction.FinancialTransaction.Direction direction) {}
    public record OAuthAuthorizeResponse(String authorizationUrl, String state) {}
    public record OAuthCallbackRequest(String code, String state) {}
    public record CreateOrderRequest(
            @NotNull @DecimalMin("0.01") BigDecimal amount,
            @NotBlank String currency,
            String description
    ) {}
    public record CaptureOrderRequest(
            String description,
            String category,
            ai.finsight.transaction.FinancialTransaction.Direction direction
    ) {}

    private UUID owner(Authentication auth) { return users.findByEmailIgnoreCase(auth.getName()).orElseThrow().getId(); }

    @GetMapping("/status")
    public PayPalImportPersistenceService.ConnectionStatus status(Authentication auth) {
        return persistence.status(owner(auth), apiClient.isConfigured());
    }

    @PostMapping("/disconnect")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disconnect(Authentication auth) {
        persistence.disconnect(owner(auth));
    }

    @GetMapping("/review")
    public java.util.List<PayPalImportPersistenceService.ReviewItem> reviews(Authentication auth) {
        return persistence.reviews(owner(auth));
    }

    @PostMapping("/review/{id}/dismiss")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void dismissReview(@PathVariable UUID id, Authentication auth) {
        persistence.dismissReview(owner(auth), id);
    }

    @PostMapping("/review/{id}/approve")
    public ai.finsight.transaction.FinancialTransaction approveReview(@PathVariable UUID id, @RequestBody(required = false) ApproveReviewRequest request, Authentication auth) {
        String desc = request != null ? request.description() : null;
        String cat = request != null ? request.category() : null;
        var dir = request != null ? request.direction() : null;
        return persistence.approveReview(owner(auth), id, desc, cat, dir);
    }

    @GetMapping("/oauth/authorize")
    public OAuthAuthorizeResponse authorizeOAuth(Authentication auth) {
        String state = UUID.randomUUID().toString().replace("-", "");
        String clientId = apiClient.getClientId();
        String redirectUri = "http://localhost:5173/accounts";
        String authUrl = "https://www.sandbox.paypal.com/signin/authorize?client_id=" + clientId
                + "&response_type=code&scope=openid%20profile%20email%20https://uri.paypal.com/services/reporting/search/read"
                + "&redirect_uri=" + java.net.URLEncoder.encode(redirectUri, java.nio.charset.StandardCharsets.UTF_8)
                + "&state=" + state;
        return new OAuthAuthorizeResponse(authUrl, state);
    }

    @PostMapping("/oauth/callback")
    public PayPalImportPersistenceService.ConnectionStatus handleOAuthCallback(@RequestBody OAuthCallbackRequest request, Authentication auth) {
        if (request == null || request.state() == null || request.state().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid OAuth state parameter.");
        }
        return persistence.status(owner(auth), apiClient.isConfigured());
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

    @PostMapping("/orders")
    public PayPalOrderResponse createOrder(@Valid @RequestBody CreateOrderRequest request, Authentication auth) {
        owner(auth);
        return gateway.createOrder(request.amount(), request.currency(), request.description());
    }

    @PostMapping("/orders/{id}/capture")
    public ai.finsight.transaction.FinancialTransaction captureOrder(
            @PathVariable("id") String orderId,
            @RequestBody(required = false) CaptureOrderRequest request,
            Authentication auth
    ) {
        UUID ownerId = owner(auth);
        persistence.assertCanConnect(ownerId);
        PayPalCaptureResponse capture = gateway.captureOrder(orderId);
        String desc = request != null ? request.description() : null;
        String cat = request != null ? request.category() : null;
        var dir = request != null ? request.direction() : null;
        return persistence.recordOrderCapture(ownerId, capture, desc, cat, dir);
    }
}
