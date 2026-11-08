package ai.finsight.paypal;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.time.*;
import java.util.*;

@Component
public class PayPalApiClient implements PayPalGateway {
    private static final int PAGE_SIZE = 100;
    private static final int MAX_PAGES = 10;
    private static final String SANDBOX = "https://api-m.sandbox.paypal.com";
    private final String clientId;
    private final String clientSecret;
    private final String baseUrl;
    private final RestClient client;
    private volatile CachedToken cachedToken;
    public boolean isConfigured() { return !clientId.isBlank() && !clientSecret.isBlank(); }
    public String getClientId() { return clientId; }

    @Autowired
    public PayPalApiClient(RestClient.Builder builder,
            @Value("${paypal.client-id:}") String clientId,
            @Value("${paypal.client-secret:}") String clientSecret,
            @Value("${paypal.base-url:https://api-m.sandbox.paypal.com}") String baseUrl) {
        this.clientId = clientId; this.clientSecret = clientSecret;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        if (!SANDBOX.equals(this.baseUrl)) throw new IllegalArgumentException("PayPal integration only permits the Sandbox API host");
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3)); factory.setReadTimeout(Duration.ofSeconds(8));
        this.client = builder.clone().requestFactory(factory).build();
    }

    PayPalApiClient(RestClient client, String clientId, String clientSecret, String baseUrl) {
        this.clientId = clientId; this.clientSecret = clientSecret;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        if (!SANDBOX.equals(this.baseUrl)) throw new IllegalArgumentException("PayPal integration only permits the Sandbox API host");
        this.client = client;
    }

    @Override public List<PayPalTransactionData> search(LocalDate startDate, LocalDate endDate) {
        requireConfigured();
        String token = accessToken();
        List<PayPalTransactionData> result = new ArrayList<>();
        String accountNumber = null;
        for (int page = 1; page <= MAX_PAGES; page++) {
            JsonNode response = getPage(token, startDate, endDate, page);
            JsonNode account = response.path("account_number");
            if (!account.isMissingNode() && !account.isNull()) {
                String value = account.asText();
                if (accountNumber != null && !accountNumber.equals(value)) throw unavailable();
                accountNumber = value;
            }
            JsonNode details = response.path("transaction_details");
            if (details.isArray()) details.forEach(item -> result.add(parse(item.path("transaction_info"))));
            int pages = response.path("total_pages").asInt(1);
            if (pages == 0) return List.copyOf(result);
            if (pages < 1 || pages > MAX_PAGES) throw unavailable();
            if (page >= pages) return List.copyOf(result);
        }
        throw unavailable();
    }

    @Override
    public PayPalOrderResponse createOrder(BigDecimal amount, String currency, String description) {
        requireConfigured();
        String token = accessToken();
        String currencyCode = (currency != null && !currency.isBlank()) ? currency.toUpperCase().trim() : "USD";
        BigDecimal orderAmount = (amount != null) ? amount.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        String desc = (description != null && !description.isBlank()) ? description.trim() : "FinSight AI Payment";

        Map<String, Object> orderPayload = new HashMap<>();
        orderPayload.put("intent", "CAPTURE");

        Map<String, Object> amountMap = new HashMap<>();
        amountMap.put("currency_code", currencyCode);
        amountMap.put("value", orderAmount.toPlainString());

        Map<String, Object> unit = new HashMap<>();
        unit.put("reference_id", "finsight-" + UUID.randomUUID());
        unit.put("description", desc);
        unit.put("amount", amountMap);

        orderPayload.put("purchase_units", List.of(unit));

        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                JsonNode response = client.post()
                        .uri(URI.create(baseUrl + "/v2/checkout/orders"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("Prefer", "return=representation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(orderPayload)
                        .retrieve()
                        .body(JsonNode.class);

                if (response == null) throw unavailable();
                String orderId = text(response, "id");
                String status = text(response, "status");
                String approveUrl = "";
                JsonNode links = response.path("links");
                if (links.isArray()) {
                    for (JsonNode link : links) {
                        if ("approve".equalsIgnoreCase(text(link, "rel"))) {
                            approveUrl = text(link, "href");
                            break;
                        }
                    }
                }
                if (orderId.isBlank()) throw unavailable();
                return new PayPalOrderResponse(orderId, status, approveUrl);
            } catch (HttpStatusCodeException ex) {
                if (ex.getStatusCode().value() == 401 && attempt == 0) {
                    synchronized (this) { cachedToken = null; }
                    token = accessToken();
                    continue;
                }
                if ((ex.getStatusCode().value() == 429 || ex.getStatusCode().is5xxServerError()) && attempt == 0) {
                    pause();
                    continue;
                }
                throw unavailable();
            } catch (ResourceAccessException ex) {
                if (attempt == 0) { pause(); continue; }
                throw unavailable();
            } catch (RestClientException ex) {
                throw unavailable();
            }
        }
        throw unavailable();
    }

    @Override
    public PayPalCaptureResponse captureOrder(String orderId) {
        requireConfigured();
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("Invalid PayPal Order ID");
        }
        String token = accessToken();
        URI uri = URI.create(baseUrl + "/v2/checkout/orders/" + orderId.trim() + "/capture");

        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                JsonNode response = client.post()
                        .uri(uri)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("Prefer", "return=representation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{}")
                        .retrieve()
                        .body(JsonNode.class);

                if (response == null) throw unavailable();
                String id = text(response, "id");
                String status = text(response, "status");

                String captureId = "";
                BigDecimal captureAmount = null;
                String currency = "USD";
                BigDecimal fee = null;

                JsonNode purchaseUnits = response.path("purchase_units");
                if (purchaseUnits.isArray() && !purchaseUnits.isEmpty()) {
                    JsonNode captures = purchaseUnits.get(0).path("payments").path("captures");
                    if (captures.isArray() && !captures.isEmpty()) {
                        JsonNode cap = captures.get(0);
                        captureId = text(cap, "id");
                        JsonNode amtNode = cap.path("amount");
                        captureAmount = decimal(amtNode, "value");
                        currency = text(amtNode, "currency_code");
                        JsonNode breakdown = cap.path("seller_receivable_breakdown");
                        if (!breakdown.isMissingNode()) {
                            fee = decimal(breakdown.path("paypal_fee"), "value");
                        }
                    }
                }

                String payerEmail = text(response.path("payer"), "email_address");
                return new PayPalCaptureResponse(id, status, captureId, captureAmount, currency, fee, payerEmail);
            } catch (HttpStatusCodeException ex) {
                if (ex.getStatusCode().value() == 401 && attempt == 0) {
                    synchronized (this) { cachedToken = null; }
                    token = accessToken();
                    continue;
                }
                if ((ex.getStatusCode().value() == 429 || ex.getStatusCode().is5xxServerError()) && attempt == 0) {
                    pause();
                    continue;
                }
                throw unavailable();
            } catch (ResourceAccessException ex) {
                if (attempt == 0) { pause(); continue; }
                throw unavailable();
            } catch (RestClientException ex) {
                throw unavailable();
            }
        }
        throw unavailable();
    }

    private JsonNode getPage(String token, LocalDate startDate, LocalDate endDate, int page) {
        URI uri = URI.create(baseUrl + "/v1/reporting/transactions?start_date=" + encode(startDate, false)
                + "&end_date=" + encode(endDate, true) + "&page=" + page + "&page_size=" + PAGE_SIZE
                + "&fields=transaction_info&balance_affecting_records_only=Y");
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                return client.get().uri(uri).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .accept(MediaType.APPLICATION_JSON).retrieve().body(JsonNode.class);
            } catch (HttpStatusCodeException ex) {
                if (ex.getStatusCode().value() == 403) throw new PayPalClientException(PayPalClientException.Kind.REPORTING_PERMISSION);
                if (ex.getStatusCode().value() == 401 && attempt == 0) { synchronized (this) { cachedToken = null; } token = accessToken(); continue; }
                if ((ex.getStatusCode().value() == 429 || ex.getStatusCode().is5xxServerError()) && attempt == 0) { pause(); continue; }
                throw unavailable();
            } catch (ResourceAccessException ex) {
                if (attempt == 0) { pause(); continue; }
                throw unavailable();
            } catch (RestClientException | IllegalArgumentException ex) { throw unavailable(); }
        }
        throw unavailable();
    }

    private synchronized String accessToken() {
        Instant now = Instant.now();
        if (cachedToken != null && now.plusSeconds(60).isBefore(cachedToken.expiresAt())) return cachedToken.value();
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                JsonNode response = client.post().uri(URI.create(baseUrl + "/v1/oauth2/token"))
                        .headers(h -> h.setBasicAuth(clientId, clientSecret))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body("grant_type=client_credentials").retrieve().body(JsonNode.class);
                String value = response == null ? "" : response.path("access_token").asText("");
                long expires = response == null ? 0 : response.path("expires_in").asLong(0);
                if (value.isBlank() || expires <= 0) throw unavailable();
                cachedToken = new CachedToken(value, now.plusSeconds(expires));
                return value;
            } catch (HttpStatusCodeException ex) {
                if ((ex.getStatusCode().value() == 429 || ex.getStatusCode().is5xxServerError()) && attempt == 0) { pause(); continue; }
                throw unavailable();
            } catch (ResourceAccessException ex) {
                if (attempt == 0) { pause(); continue; }
                throw unavailable();
            } catch (RestClientException ex) { throw unavailable(); }
        }
        throw unavailable();
    }

    private PayPalTransactionData parse(JsonNode info) {
        String id = text(info, "transaction_id");
        String status = text(info, "transaction_status");
        String code = text(info, "transaction_event_code");
        LocalDate occurred = null;
        String date = text(info, "transaction_initiation_date");
        if (!date.isBlank()) try { occurred = OffsetDateTime.parse(date).toLocalDate(); } catch (RuntimeException ignored) { }
        JsonNode amount = info.path("transaction_amount");
        String currency = text(amount, "currency_code");
        BigDecimal value = decimal(amount, "value");
        BigDecimal fee = decimal(info.path("fee_amount"), "value");
        String feeCurrency = text(info.path("fee_amount"), "currency_code");
        if (fee != null && !feeCurrency.isBlank() && !feeCurrency.equals(currency)) fee = new BigDecimal("-1");
        return new PayPalTransactionData(id, status, code, occurred, value, currency, fee, PayPalTransactionData.fingerprint(info.toString()));
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        String value = text(node, field);
        if (value.isBlank()) return null;
        try { return new BigDecimal(value); } catch (NumberFormatException ex) { return null; }
    }
    private static String text(JsonNode node, String field) { JsonNode value = node.path(field); return value.isMissingNode() || value.isNull() ? "" : value.asText(""); }
    private static String encode(LocalDate date, boolean end) {
        LocalDateTime time = end ? date.atTime(23, 59, 59, 999_000_000) : date.atStartOfDay();
        String value = time.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))
                + (end ? ".999Z" : "Z");
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }
    private void requireConfigured() { if (clientId.isBlank() || clientSecret.isBlank()) throw new PayPalClientException(PayPalClientException.Kind.NOT_CONFIGURED); }
    private static PayPalClientException unavailable() { return new PayPalClientException(PayPalClientException.Kind.UPSTREAM_UNAVAILABLE); }
    private static void pause() { try { Thread.sleep(150); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw unavailable(); } }
    private record CachedToken(String value, Instant expiresAt) {}
}
