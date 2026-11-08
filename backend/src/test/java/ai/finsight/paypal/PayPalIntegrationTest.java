package ai.finsight.paypal;

import ai.finsight.transaction.TransactionRepository;
import ai.finsight.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={
        "spring.datasource.url=jdbc:h2:mem:paypaltest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect", "app.cors-origin=http://localhost:5173"
})
@AutoConfigureMockMvc
class PayPalIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired TransactionRepository transactions;
    @Autowired UserRepository users;
    @Autowired PayPalConnectionRepository connections;
    @Autowired PayPalImportRecordRepository imports;
    @MockBean PayPalApiClient paypal;

    @org.junit.jupiter.api.BeforeEach
    void clean() {
        imports.deleteAll();
        connections.deleteAll();
        transactions.deleteAll();
    }

    @Test void authenticatedSandboxImportIsCsrfProtectedDeduplicatedReviewableAndOwnerScoped() throws Exception {
        when(paypal.isConfigured()).thenReturn(true);
        mvc.perform(get("/api/integrations/paypal/status")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/integrations/paypal/import").contentType(MediaType.APPLICATION_JSON)
                .content("{\"startDate\":\"2026-10-01\",\"endDate\":\"2026-10-02\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/integrations/paypal/import").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"startDate\":\"2026-10-01\",\"endDate\":\"2026-10-02\"}")).andExpect(status().isUnauthorized());

        MockHttpSession alice = sessionFor("paypal-alice-"+UUID.randomUUID()+"@example.com", "Alice");
        MockHttpSession bob = sessionFor("paypal-bob-"+UUID.randomUUID()+"@example.com", "Bob");
        String request = "{\"startDate\":\""+LocalDate.now().minusDays(2)+"\",\"endDate\":\""+LocalDate.now().minusDays(1)+"\"}";
        mvc.perform(post("/api/integrations/paypal/import").session(alice).contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isForbidden());

        when(paypal.search(any(),any())).thenThrow(new PayPalClientException(PayPalClientException.Kind.REPORTING_PERMISSION));
        mvc.perform(post("/api/integrations/paypal/import").session(alice).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isBadGateway());
        mvc.perform(get("/api/integrations/paypal/status").session(alice)).andExpect(status().isOk()).andExpect(jsonPath("$.connected").value(false));

        LocalDate date=LocalDate.now().minusDays(1);
        PayPalTransactionData supported = new PayPalTransactionData("PAYPALTXN00000000000001", "S", "T0006", date, new BigDecimal("1250.25"), "INR", BigDecimal.ZERO);
        PayPalTransactionData foreignCurrency = new PayPalTransactionData("PAYPALTXN00000000000002", "S", "T0006", date, new BigDecimal("10.00"), "USD", BigDecimal.ZERO);
        PayPalTransactionData fee = new PayPalTransactionData("PAYPALTXN00000000000003", "S", "T0007", date, new BigDecimal("25.00"), "INR", new BigDecimal("1.00"));
        PayPalTransactionData reversed = new PayPalTransactionData("PAYPALTXN00000000000004", "V", "T1107", date, new BigDecimal("5.00"), "INR", BigDecimal.ZERO);
        PayPalTransactionData missingId = new PayPalTransactionData(null, "S", "T0006", date, new BigDecimal("5.00"), "INR", BigDecimal.ZERO);
        doReturn(List.of(supported,foreignCurrency,fee,reversed,missingId)).when(paypal).search(any(),any());
        mvc.perform(post("/api/integrations/paypal/import").session(alice).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk()).andExpect(jsonPath("$.environment").value("SANDBOX"))
                .andExpect(jsonPath("$.importedCount").value(0)).andExpect(jsonPath("$.duplicateCount").value(0)).andExpect(jsonPath("$.reviewCount").value(5));

        String aliceEmail=json.readTree(mvc.perform(get("/api/auth/me").session(alice)).andReturn().getResponse().getContentAsString()).get("email").asText();
        UUID aliceId=users.findByEmailIgnoreCase(aliceEmail).orElseThrow().getId();
        var saved=transactions.findAllByUserIdOrderByOccurredOnDesc(aliceId);
        assertTrue(saved.isEmpty());
        MvcResult review=mvc.perform(get("/api/integrations/paypal/review").session(alice)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5)).andReturn();
        assertTrue(review.getResponse().getContentAsString().contains("UNVERIFIED_AMOUNT_DIRECTION"));
        mvc.perform(get("/api/transactions").session(bob)).andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(get("/api/integrations/paypal/status").session(bob)).andExpect(status().isOk()).andExpect(jsonPath("$.connected").value(false));
        mvc.perform(get("/api/integrations/paypal/review").session(bob)).andExpect(status().isOk()).andExpect(content().json("[]"));

        mvc.perform(post("/api/integrations/paypal/import").session(alice).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk()).andExpect(jsonPath("$.importedCount").value(0)).andExpect(jsonPath("$.duplicateCount").value(5));
        mvc.perform(post("/api/integrations/paypal/import").session(bob).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isConflict());
        verify(paypal,times(3)).search(any(),any());
    }

    @Test void createsAndCapturesSandboxOrderSuccessfully() throws Exception {
        when(paypal.isConfigured()).thenReturn(true);
        MockHttpSession userSession = sessionFor("paypal-orders-"+UUID.randomUUID()+"@example.com", "OrderTester");

        when(paypal.createOrder(any(), any(), any())).thenReturn(new PayPalOrderResponse("ORD-999", "CREATED", "https://sandbox.paypal.com/checkoutnow?token=ORD-999"));

        mvc.perform(post("/api/integrations/paypal/orders")
                .session(userSession).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":150.00,\"currency\":\"USD\",\"description\":\"Cloud Hosting Bill\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value("ORD-999"))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.approveUrl").value("https://sandbox.paypal.com/checkoutnow?token=ORD-999"));

        when(paypal.captureOrder("ORD-999")).thenReturn(new PayPalCaptureResponse(
                "ORD-999", "COMPLETED", "CAP-999", new BigDecimal("150.00"), "USD", new BigDecimal("4.50"), "payer@example.com"
        ));

        mvc.perform(post("/api/integrations/paypal/orders/ORD-999/capture")
                .session(userSession).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":\"Cloud Hosting Bill\",\"category\":\"Technology\",\"direction\":\"EXPENSE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(150.00))
                .andExpect(jsonPath("$.category").value("Technology"))
                .andExpect(jsonPath("$.direction").value("EXPENSE"))
                .andExpect(jsonPath("$.sourceProvider").value("PAYPAL"));

        mvc.perform(get("/api/integrations/paypal/status").session(userSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connected").value(true));
    }

    private MockHttpSession sessionFor(String email,String name) throws Exception {
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\""+email+"\",\"password\":\"a-long-test-password\",\"displayName\":\""+name+"\"}")).andExpect(status().isCreated());
        MvcResult login=mvc.perform(post("/api/auth/login").with(csrf()).param("email",email).param("password","a-long-test-password"))
                .andExpect(status().isNoContent()).andReturn();
        return (MockHttpSession)login.getRequest().getSession(false);
    }
}
