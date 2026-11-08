package ai.finsight.paypal;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class PayPalApiClientTest {
    private static final String TOKEN = "opaque-test-access-token";
    private static final String BASE = "https://api-m.sandbox.paypal.com";

    @Test void getsOAuthTokenOnceAndReadsAllPages() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PayPalApiClient client = new PayPalApiClient(builder.build(), "client-id", "client-secret", BASE);
        server.expect(requestTo(BASE + "/v1/oauth2/token")).andExpect(method(HttpMethod.POST)).andRespond(withSuccess("{\"access_token\":\""+TOKEN+"\",\"expires_in\":3600}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/v1/reporting/transactions?")))
                .andExpect(queryParam("start_date","2026-10-01T00%3A00%3A00Z"))
                .andExpect(queryParam("end_date","2026-10-02T23%3A59%3A59.999Z"))
                .andExpect(queryParam("balance_affecting_records_only","Y"))
                .andExpect(header("Authorization", "Bearer "+TOKEN))
                .andRespond(withSuccess(report(2, "TXN00000000000000000001", "1.25"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("page=2"))).andExpect(header("Authorization", "Bearer "+TOKEN)).andRespond(withSuccess(report(2, "TXN00000000000000000002", "-2.50"), MediaType.APPLICATION_JSON));
        List<PayPalTransactionData> result = client.search(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-02"));
        assertEquals(2,result.size()); assertEquals("INR",result.getFirst().currencyCode()); assertEquals("-2.50",result.get(1).signedAmount().toPlainString());
        server.verify();
    }

    @Test void refreshesExpiredTokenAfterUnauthorizedReportResponse() {
        RestClient.Builder builder = RestClient.builder(); MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PayPalApiClient client = new PayPalApiClient(builder.build(), "id", "secret", BASE);
        server.expect(requestTo(BASE + "/v1/oauth2/token")).andRespond(withSuccess("{\"access_token\":\"old-token\",\"expires_in\":3600}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/v1/reporting/transactions?"))).andRespond(withUnauthorizedRequest());
        server.expect(requestTo(BASE + "/v1/oauth2/token")).andRespond(withSuccess("{\"access_token\":\"new-token\",\"expires_in\":3600}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/v1/reporting/transactions?"))).andExpect(header("Authorization", "Bearer new-token")).andRespond(withSuccess("{\"total_pages\":1,\"transaction_details\":[]}", MediaType.APPLICATION_JSON));
        assertTrue(client.search(LocalDate.now().minusDays(1), LocalDate.now()).isEmpty()); server.verify();
    }

    @Test void reportsOAuthFailureWithoutLeakingProviderBody() {
        RestClient.Builder builder = RestClient.builder(); MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PayPalApiClient client = new PayPalApiClient(builder.build(), "id", "secret", BASE);
        server.expect(requestTo(BASE + "/v1/oauth2/token")).andRespond(withUnauthorizedRequest());
        PayPalClientException error = assertThrows(PayPalClientException.class, () -> client.search(LocalDate.now().minusDays(1), LocalDate.now()));
        assertEquals(PayPalClientException.Kind.UPSTREAM_UNAVAILABLE,error.getKind()); server.verify();
    }

    @Test void retriesTransientReportingFailureAndRejectsNonSandboxHost() {
        RestClient.Builder builder = RestClient.builder(); MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PayPalApiClient client = new PayPalApiClient(builder.build(), "id", "secret", BASE);
        server.expect(requestTo(BASE + "/v1/oauth2/token")).andRespond(withSuccess("{\"access_token\":\"tok\",\"expires_in\":3600}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/v1/reporting/transactions?"))).andRespond(withServerError());
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/v1/reporting/transactions?"))).andRespond(withSuccess("{\"total_pages\":1,\"transaction_details\":[]}", MediaType.APPLICATION_JSON));
        assertTrue(client.search(LocalDate.now().minusDays(1), LocalDate.now()).isEmpty()); server.verify();
        assertThrows(IllegalArgumentException.class,()->new PayPalApiClient(RestClient.builder().build(),"id","secret","https://api-m.paypal.com"));
    }

    @Test void retriesTimeoutOnceThenReturnsSafeUpstreamFailure() {
        RestClient.Builder builder=RestClient.builder(); MockRestServiceServer server=MockRestServiceServer.bindTo(builder).build();
        PayPalApiClient client=new PayPalApiClient(builder.build(),"id","secret",BASE);
        server.expect(requestTo(BASE+"/v1/oauth2/token")).andRespond(withSuccess("{\"access_token\":\"tok\",\"expires_in\":3600}",MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/v1/reporting/transactions?"))).andRespond(withException(new java.net.SocketTimeoutException("timeout")));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/v1/reporting/transactions?"))).andRespond(withException(new java.net.SocketTimeoutException("timeout")));
        PayPalClientException error=assertThrows(PayPalClientException.class,()->client.search(LocalDate.now().minusDays(1),LocalDate.now()));
        assertEquals(PayPalClientException.Kind.UPSTREAM_UNAVAILABLE,error.getKind()); server.verify();
    }

    @Test void createsCheckoutOrderWithCorrectPayloadAndExtractsApproveUrl() {
        RestClient.Builder builder = RestClient.builder(); MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PayPalApiClient client = new PayPalApiClient(builder.build(), "id", "secret", BASE);
        server.expect(requestTo(BASE + "/v1/oauth2/token")).andRespond(withSuccess("{\"access_token\":\"tok\",\"expires_in\":3600}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/v2/checkout/orders"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer tok"))
                .andExpect(jsonPath("$.intent").value("CAPTURE"))
                .andExpect(jsonPath("$.purchase_units[0].amount.currency_code").value("USD"))
                .andExpect(jsonPath("$.purchase_units[0].amount.value").value("50.00"))
                .andRespond(withSuccess("{\"id\":\"ORDER123\",\"status\":\"CREATED\",\"links\":[{\"href\":\"https://www.sandbox.paypal.com/checkoutnow?token=ORDER123\",\"rel\":\"approve\"}]}", MediaType.APPLICATION_JSON));

        PayPalOrderResponse response = client.createOrder(new java.math.BigDecimal("50.00"), "USD", "Test Checkout");
        assertEquals("ORDER123", response.orderId());
        assertEquals("CREATED", response.status());
        assertEquals("https://www.sandbox.paypal.com/checkoutnow?token=ORDER123", response.approveUrl());
        server.verify();
    }

    @Test void capturesCheckoutOrderAndExtractsPaymentDetails() {
        RestClient.Builder builder = RestClient.builder(); MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PayPalApiClient client = new PayPalApiClient(builder.build(), "id", "secret", BASE);
        server.expect(requestTo(BASE + "/v1/oauth2/token")).andRespond(withSuccess("{\"access_token\":\"tok\",\"expires_in\":3600}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/v2/checkout/orders/ORDER123/capture"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer tok"))
                .andRespond(withSuccess("{\"id\":\"ORDER123\",\"status\":\"COMPLETED\",\"purchase_units\":[{\"payments\":{\"captures\":[{\"id\":\"CAP123\",\"amount\":{\"currency_code\":\"USD\",\"value\":\"50.00\"},\"seller_receivable_breakdown\":{\"paypal_fee\":{\"value\":\"1.75\",\"currency_code\":\"USD\"}}}]}}],\"payer\":{\"email_address\":\"buyer@sandbox.paypal.com\"}}", MediaType.APPLICATION_JSON));

        PayPalCaptureResponse capture = client.captureOrder("ORDER123");
        assertEquals("ORDER123", capture.orderId());
        assertEquals("COMPLETED", capture.status());
        assertEquals("CAP123", capture.captureId());
        assertEquals("50.00", capture.amount().toPlainString());
        assertEquals("USD", capture.currency());
        assertEquals("1.75", capture.fee().toPlainString());
        assertEquals("buyer@sandbox.paypal.com", capture.payerEmail());
        server.verify();
    }

    private static String report(int pages,String id,String amount) {
        return "{\"account_number\":\"masked\",\"total_pages\":"+pages+",\"transaction_details\":[{\"transaction_info\":{\"transaction_id\":\""+id+"\",\"transaction_status\":\"S\",\"transaction_event_code\":\"T0006\",\"transaction_initiation_date\":\"2026-10-01T10:00:00Z\",\"transaction_amount\":{\"value\":\""+amount+"\",\"currency_code\":\"INR\"}}}]}";
    }
}
