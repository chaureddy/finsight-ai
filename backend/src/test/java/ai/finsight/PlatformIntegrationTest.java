package ai.finsight;

import com.fasterxml.jackson.databind.ObjectMapper;
import ai.finsight.recurring.RecurringOccurrence;
import ai.finsight.recurring.RecurringOccurrenceRepository;
import ai.finsight.recurring.RecurringTransaction;
import ai.finsight.recurring.RecurringTransactionRepository;
import ai.finsight.transaction.FinancialTransaction;
import ai.finsight.transaction.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import java.time.LocalDate;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={
 "spring.datasource.url=jdbc:h2:mem:finsight;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
 "spring.datasource.driver-class-name=org.h2.Driver",
 "spring.datasource.username=sa",
 "spring.datasource.password=",
 "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
 "app.cors-origin=http://localhost:5173"
})
@AutoConfigureMockMvc
class PlatformIntegrationTest {
 @Autowired MockMvc mvc;
 @Autowired ObjectMapper json;
 @Autowired TransactionRepository transactions;
 @Autowired RecurringTransactionRepository recurringTransactions;
 @Autowired RecurringOccurrenceRepository occurrences;

 @Test void financialIntelligenceEndpointsRequireAuthentication() throws Exception {
  mvc.perform(get("/api/budgets")).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/goals")).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/recurring-transactions")).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/forecast")).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/financial-health")).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/intelligence/spending")).andExpect(status().isUnauthorized());
 }

 @Test void registrationSessionCrudIsolationAndLogout() throws Exception {
  MockHttpSession alice=sessionFor("alice@example.com","Alice Doe");
  MockHttpSession bob=sessionFor("bob@example.com","Bob Doe");

  mvc.perform(get("/api/auth/me").session(alice)).andExpect(status().isOk()).andExpect(jsonPath("$.email").value("alice@example.com"));
  String today=LocalDate.now().toString();
  String transactionBody="""
   {"description":"Salary","category":"Income","amount":85000.00,"direction":"INCOME","occurredOn":"%s"}
   """.formatted(today);
  MvcResult created=mvc.perform(post("/api/transactions").session(alice).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(transactionBody))
   .andExpect(status().isCreated()).andExpect(jsonPath("$.description").value("Salary")).andReturn();
  String id=json.readTree(created.getResponse().getContentAsString()).get("id").asText();

  mvc.perform(get("/api/transactions").session(alice)).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(id));
  mvc.perform(get("/api/transactions").session(bob)).andExpect(status().isOk()).andExpect(content().json("[]"));
  mvc.perform(put("/api/transactions/{id}",id).session(bob).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(transactionBody)).andExpect(status().isNotFound());
  mvc.perform(delete("/api/transactions/{id}",id).session(bob).with(csrf())).andExpect(status().isNotFound());

  String updated="""
   {"description":"Updated salary","category":"Work","amount":90000.00,"direction":"INCOME","occurredOn":"%s"}
   """.formatted(today);
  mvc.perform(put("/api/transactions/{id}",id).session(alice).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(updated))
   .andExpect(status().isOk()).andExpect(jsonPath("$.description").value("Updated salary"));
  mvc.perform(get("/api/analytics/cash-flow").session(alice)).andExpect(status().isOk()).andExpect(jsonPath("$.income").value(90000));
  mvc.perform(delete("/api/transactions/{id}",id).session(alice).with(csrf())).andExpect(status().isNoContent());

  mvc.perform(post("/api/auth/logout").session(alice).with(csrf())).andExpect(status().isNoContent());
  mvc.perform(get("/api/auth/me").session(alice)).andExpect(status().isUnauthorized());
 }

 @Test void csrfBootstrapTokenCanAuthorizeBrowserStyleRegistration() throws Exception {
  MvcResult tokenResult=mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN")).andReturn();
  var token=json.readTree(tokenResult.getResponse().getContentAsString());
  MockHttpSession session=(MockHttpSession)tokenResult.getRequest().getSession(false);
  mvc.perform(post("/api/auth/register").session(session).header(token.get("headerName").asText(),token.get("token").asText())
    .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"csrf@example.com\",\"password\":\"a-long-test-password\",\"displayName\":\"CSRF User\"}"))
   .andExpect(status().isCreated());
 }

 @Test void loginRotatesCsrfTokenAndFreshSessionTokenCanCreateTransaction() throws Exception {
  String email="csrf-rotation-"+java.util.UUID.randomUUID()+"@example.com";
  MvcResult bootstrap=mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
  var initialToken=json.readTree(bootstrap.getResponse().getContentAsString());
  MockHttpSession session=(MockHttpSession)bootstrap.getRequest().getSession(false);
  String csrfHeader=initialToken.get("headerName").asText();
  String staleToken=initialToken.get("token").asText();
  String registration="""
   {"email":"%s","password":"a-long-test-password","displayName":"CSRF Rotation"}
   """.formatted(email);
  mvc.perform(post("/api/auth/register").session(session).header(csrfHeader,staleToken)
    .contentType(MediaType.APPLICATION_JSON).content(registration)).andExpect(status().isCreated());

  MvcResult login=mvc.perform(post("/api/auth/login").session(session).header(csrfHeader,staleToken)
    .param("email",email).param("password","a-long-test-password")).andExpect(status().isNoContent()).andReturn();
  MockHttpSession authenticatedSession=(MockHttpSession)login.getRequest().getSession(false);
  String transaction="""
   {"description":"Salary","category":"Income","amount":1200.50,"direction":"INCOME","occurredOn":"%s"}
   """.formatted(LocalDate.now());

  mvc.perform(post("/api/transactions").session(authenticatedSession).header(csrfHeader,staleToken)
    .contentType(MediaType.APPLICATION_JSON).content(transaction)).andExpect(status().isForbidden());

  MvcResult refreshed=mvc.perform(get("/api/auth/csrf").session(authenticatedSession)).andExpect(status().isOk()).andReturn();
  var freshToken=json.readTree(refreshed.getResponse().getContentAsString());
  mvc.perform(post("/api/transactions").session(authenticatedSession)
    .header(freshToken.get("headerName").asText(),freshToken.get("token").asText())
    .contentType(MediaType.APPLICATION_JSON).content(transaction))
   .andExpect(status().isCreated()).andExpect(jsonPath("$.description").value("Salary"));
 }

 @Test void financialManagementCrudIsOwnerScopedAndOverviewUsesBigDecimalValues() throws Exception {
  MockHttpSession alice=sessionFor("finance-alice-"+java.util.UUID.randomUUID()+"@example.com","Finance Alice");
  MockHttpSession bob=sessionFor("finance-bob-"+java.util.UUID.randomUUID()+"@example.com","Finance Bob");
  String today=LocalDate.now().toString();
  String asset="""
   {"name":"Savings","category":"Bank Account","currentValue":1000.25,"purchaseValue":900.00,"purchaseDate":"%s","description":"Test asset"}
   """.formatted(today);
  MvcResult assetResult=mvc.perform(post("/api/assets").session(alice).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(asset)).andExpect(status().isCreated()).andExpect(jsonPath("$.currentValue").value(1000.25)).andReturn();
  String assetId=json.readTree(assetResult.getResponse().getContentAsString()).get("id").asText();
  String aliceUserId=mvc.perform(get("/api/auth/me").session(alice)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  String ownerInjection=asset.replace("{","{\"userId\":\""+json.readTree(aliceUserId).get("id").asText()+"\",");
  mvc.perform(post("/api/assets").session(bob).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(ownerInjection)).andExpect(status().isBadRequest());
  mvc.perform(get("/api/assets").session(bob)).andExpect(status().isOk()).andExpect(content().json("[]"));
  mvc.perform(put("/api/assets/{id}",assetId).session(bob).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(asset)).andExpect(status().isNotFound());
  mvc.perform(delete("/api/assets/{id}",assetId).session(bob).with(csrf())).andExpect(status().isNotFound());
  mvc.perform(put("/api/assets/{id}",assetId).session(alice).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(asset.replace("1000.25","1100.25"))).andExpect(status().isOk()).andExpect(jsonPath("$.currentValue").value(1100.25));

  String liability="""
   {"name":"Education debt","category":"Education Loan","principalAmount":5000,"outstandingAmount":4000.50,"interestRate":7.5000,"monthlyEmi":250,"startDate":"%s","dueDate":null,"description":"Test liability"}
   """.formatted(today);
  MvcResult liabilityResult=mvc.perform(post("/api/liabilities").session(alice).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(liability)).andExpect(status().isCreated()).andReturn();
  String liabilityId=json.readTree(liabilityResult.getResponse().getContentAsString()).get("id").asText();
  mvc.perform(get("/api/liabilities").session(bob)).andExpect(status().isOk()).andExpect(content().json("[]"));
  mvc.perform(put("/api/liabilities/{id}",liabilityId).session(bob).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(liability)).andExpect(status().isNotFound());
  mvc.perform(delete("/api/liabilities/{id}",liabilityId).session(bob).with(csrf())).andExpect(status().isNotFound());
  mvc.perform(put("/api/liabilities/{id}",liabilityId).session(alice).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(liability.replace("4000.50","3500.50"))).andExpect(status().isOk());

  String investment="""
   {"name":"Index fund","category":"Mutual Funds","investedAmount":700,"currentValue":800.25,"purchaseDate":"%s","description":"User entered valuation"}
   """.formatted(today);
  MvcResult investmentResult=mvc.perform(post("/api/investments").session(alice).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(investment)).andExpect(status().isCreated()).andReturn();
  String investmentId=json.readTree(investmentResult.getResponse().getContentAsString()).get("id").asText();
  mvc.perform(get("/api/investments").session(bob)).andExpect(status().isOk()).andExpect(content().json("[]"));
  mvc.perform(put("/api/investments/{id}",investmentId).session(bob).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(investment)).andExpect(status().isNotFound());
  mvc.perform(delete("/api/investments/{id}",investmentId).session(bob).with(csrf())).andExpect(status().isNotFound());
  mvc.perform(put("/api/investments/{id}",investmentId).session(alice).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(investment.replace("800.25","900.25"))).andExpect(status().isOk());

  String insurance="""
   {"provider":"Example Mutual","policyName":"Health cover","category":"Health","coverageAmount":10000,"premiumAmount":120,"premiumFrequency":"Annual","startDate":"%s","renewalDate":null,"description":"Test policy"}
   """.formatted(today);
  MvcResult policyResult=mvc.perform(post("/api/insurance").session(alice).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(insurance)).andExpect(status().isCreated()).andReturn();
  String policyId=json.readTree(policyResult.getResponse().getContentAsString()).get("id").asText();
  mvc.perform(get("/api/insurance").session(bob)).andExpect(status().isOk()).andExpect(content().json("[]"));
  mvc.perform(put("/api/insurance/{id}",policyId).session(bob).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(insurance)).andExpect(status().isNotFound());
  mvc.perform(delete("/api/insurance/{id}",policyId).session(bob).with(csrf())).andExpect(status().isNotFound());
  mvc.perform(put("/api/insurance/{id}",policyId).session(alice).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(insurance.replace("10000","12000"))).andExpect(status().isOk());

  mvc.perform(post("/api/assets").session(alice).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(asset.replace("1000.25","-1")))
   .andExpect(status().isBadRequest()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("-1"))));
  mvc.perform(post("/api/investments").session(alice).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(investment.replace("Mutual Funds","Crypto scam"))).andExpect(status().isBadRequest());
  String incomeTransaction="""
   {"description":"Test income","category":"Salary","amount":1234.56,"direction":"INCOME","occurredOn":"%s"}
   """.formatted(today);
  String expenseTransaction="""
   {"description":"Test expense","category":"Food","amount":234.56,"direction":"EXPENSE","occurredOn":"%s"}
   """.formatted(today);
  mvc.perform(post("/api/transactions").session(alice).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(incomeTransaction)).andExpect(status().isCreated());
  mvc.perform(post("/api/transactions").session(alice).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(expenseTransaction)).andExpect(status().isCreated());
  mvc.perform(get("/api/financial-overview").session(alice)).andExpect(status().isOk())
   .andExpect(jsonPath("$.totalAssets").value(1100.25)).andExpect(jsonPath("$.totalInvestments").value(900.25))
   .andExpect(jsonPath("$.totalLiabilities").value(3500.50)).andExpect(jsonPath("$.netWorth").value(-1500.00))
   .andExpect(jsonPath("$.monthlyEmiCommitment").value(250)).andExpect(jsonPath("$.totalInsuranceCoverage").value(12000))
   .andExpect(jsonPath("$.monthlyIncome").value(1234.56)).andExpect(jsonPath("$.monthlyExpenses").value(234.56))
   .andExpect(jsonPath("$.monthlySavings").value(1000.00)).andExpect(jsonPath("$.savingsRatePercent").value(81.00));

  mvc.perform(delete("/api/assets/{id}",assetId).session(alice).with(csrf())).andExpect(status().isNoContent());
  mvc.perform(delete("/api/liabilities/{id}",liabilityId).session(alice).with(csrf())).andExpect(status().isNoContent());
  mvc.perform(delete("/api/investments/{id}",investmentId).session(alice).with(csrf())).andExpect(status().isNoContent());
  mvc.perform(delete("/api/insurance/{id}",policyId).session(alice).with(csrf())).andExpect(status().isNoContent());
 }

 @Test void budgetsGoalsRecurringForecastHealthAndSpendingAreOwnerScopedAndDeterministic() throws Exception {
  String suffix=java.util.UUID.randomUUID().toString();MockHttpSession alice=sessionFor("intelligence-alice-"+suffix+"@example.com","Intelligence Alice");MockHttpSession bob=sessionFor("intelligence-bob-"+suffix+"@example.com","Intelligence Bob");
  String today=LocalDate.now().toString(),monthStart=LocalDate.now().withDayOfMonth(1).toString(),monthEnd=LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth()).toString();
  String expense="""
   {"description":"Groceries","category":"Food","amount":250.00,"direction":"EXPENSE","occurredOn":"%s"}
   """.formatted(today);
  String income="""
   {"description":"Pay","category":"Salary","amount":2000.00,"direction":"INCOME","occurredOn":"%s"}
   """.formatted(today);
  mvc.perform(post("/api/transactions").session(alice).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(expense)).andExpect(status().isCreated());
  mvc.perform(post("/api/transactions").session(alice).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(income)).andExpect(status().isCreated());
  String budget="""
   {"name":"Food budget","category":"Food","monthlyLimit":1000.00,"startDate":"%s","endDate":"%s","alertThresholdPercentage":20.00}
   """.formatted(monthStart,monthEnd);
  MvcResult budgetResult=mvc.perform(post("/api/budgets").session(alice).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(budget)).andExpect(status().isCreated()).andExpect(jsonPath("$.amountSpent").value(250)).andExpect(jsonPath("$.alertTriggered").value(true)).andExpect(jsonPath("$.exceeded").value(false)).andReturn();
  String budgetId=json.readTree(budgetResult.getResponse().getContentAsString()).get("id").asText();
  mvc.perform(get("/api/budgets").session(bob)).andExpect(status().isOk()).andExpect(content().json("[]"));
  mvc.perform(put("/api/budgets/{id}",budgetId).session(bob).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(budget)).andExpect(status().isNotFound());
  mvc.perform(post("/api/budgets").session(alice).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(budget)).andExpect(status().isConflict());
  mvc.perform(post("/api/budgets").session(alice).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(budget.replace(monthEnd,"2020-01-01"))).andExpect(status().isBadRequest());

  String completedGoal="""
   {"name":"Course fund","category":"Education","targetAmount":5000.00,"currentAmount":5000.00,"targetDate":"%s","priority":"HIGH","description":"Already funded"}
   """.formatted(LocalDate.now().plusMonths(2));
  MvcResult goalResult=mvc.perform(post("/api/goals").session(alice).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(completedGoal)).andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("COMPLETED")).andExpect(jsonPath("$.progressPercentage").value(100)).andReturn();
  String goalId=json.readTree(goalResult.getResponse().getContentAsString()).get("id").asText();
  mvc.perform(get("/api/goals").session(bob)).andExpect(status().isOk()).andExpect(content().json("[]"));
  mvc.perform(put("/api/goals/{id}",goalId).session(bob).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(completedGoal)).andExpect(status().isNotFound());
  mvc.perform(post("/api/goals").session(alice).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(completedGoal.replace("\"currentAmount\":5000.00","\"currentAmount\":-1.00"))).andExpect(status().isBadRequest());

  String recurrence="""
   {"title":"Rent","type":"EXPENSE","amount":1000.00,"category":"Housing","frequency":"MONTHLY","nextDueDate":"%s","endDate":null,"active":true}
   """.formatted(today);
  MvcResult recurringResult=mvc.perform(post("/api/recurring-transactions").session(alice).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(recurrence)).andExpect(status().isCreated()).andReturn();
  String recurringId=json.readTree(recurringResult.getResponse().getContentAsString()).get("id").asText();
  mvc.perform(get("/api/recurring-transactions").session(bob)).andExpect(status().isOk()).andExpect(content().json("[]"));
  mvc.perform(put("/api/recurring-transactions/{id}",recurringId).session(bob).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(recurrence)).andExpect(status().isNotFound());
  mvc.perform(post("/api/recurring-transactions/{id}/occurrences",recurringId).session(bob).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"occurrenceDate\":\""+today+"\"}")).andExpect(status().isNotFound());
  String occurrence="""
   {"occurrenceDate":"%s"}
   """.formatted(today);
  MvcResult occurrenceResult=mvc.perform(post("/api/recurring-transactions/{id}/occurrences",recurringId).session(alice).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(occurrence)).andExpect(status().isCreated()).andExpect(jsonPath("$.transactionId").exists()).andReturn();
  UUID occurrenceTransactionId=UUID.fromString(json.readTree(occurrenceResult.getResponse().getContentAsString()).get("transactionId").asText());
  RecurringTransaction persistedRecurrence=recurringTransactions.findById(UUID.fromString(recurringId)).orElseThrow();
  RecurringOccurrence persistedOccurrence=occurrences.findByRecurringTransactionIdAndOccurrenceDate(UUID.fromString(recurringId),LocalDate.parse(today)).orElseThrow();
  FinancialTransaction persistedTransaction=transactions.findById(occurrenceTransactionId).orElseThrow();
  assertEquals(occurrenceTransactionId,persistedOccurrence.getTransactionId());
  assertEquals(persistedRecurrence.getUserId(),persistedOccurrence.getUserId());
  assertEquals(persistedRecurrence.getUserId(),persistedTransaction.getUserId());
  assertEquals("Rent",persistedTransaction.getDescription());
  assertEquals(new java.math.BigDecimal("1000.00"),persistedTransaction.getAmount());
  assertEquals(FinancialTransaction.Direction.EXPENSE,persistedTransaction.getDirection());
  assertEquals(LocalDate.parse(today),persistedTransaction.getOccurredOn());
  MvcResult aliceTransactionsResult=mvc.perform(get("/api/transactions").session(alice)).andExpect(status().isOk()).andReturn();
  var aliceTransactionsJson=json.readTree(aliceTransactionsResult.getResponse().getContentAsString());
  assertTrue(java.util.stream.StreamSupport.stream(aliceTransactionsJson.spliterator(),false).anyMatch(row->occurrenceTransactionId.toString().equals(row.get("id").asText())));
  MvcResult bobTransactionsResult=mvc.perform(get("/api/transactions").session(bob)).andExpect(status().isOk()).andReturn();
  var bobTransactionsJson=json.readTree(bobTransactionsResult.getResponse().getContentAsString());
  assertFalse(java.util.stream.StreamSupport.stream(bobTransactionsJson.spliterator(),false).anyMatch(row->occurrenceTransactionId.toString().equals(row.get("id").asText())));
  mvc.perform(post("/api/recurring-transactions/{id}/occurrences",recurringId).session(alice).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(occurrence)).andExpect(status().isConflict());
  assertEquals(1,transactions.findAllByUserIdOrderByOccurredOnDesc(persistedRecurrence.getUserId()).stream().filter(t->t.getDescription().equals("Rent")).count());
  mvc.perform(patch("/api/recurring-transactions/{id}/active",recurringId).session(alice).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}")).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
  mvc.perform(patch("/api/recurring-transactions/{id}/active",recurringId).session(alice).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"active\":true}")).andExpect(status().isOk());
  mvc.perform(post("/api/recurring-transactions").session(alice).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(recurrence.replace("MONTHLY","BIWEEKLY"))).andExpect(status().isBadRequest());

  mvc.perform(get("/api/forecast").session(alice)).andExpect(status().isOk()).andExpect(jsonPath("$.months.length()").value(3)).andExpect(jsonPath("$.confidence").value("SCHEDULE_ONLY"));
  mvc.perform(get("/api/financial-health").session(alice)).andExpect(status().isOk()).andExpect(jsonPath("$.score").exists()).andExpect(jsonPath("$.confidence").exists());
  mvc.perform(get("/api/intelligence/spending").session(alice)).andExpect(status().isOk()).andExpect(jsonPath("$.topCategories[0].category").value("Housing")).andExpect(jsonPath("$.monthlyTrends.length()").value(6));

  mvc.perform(delete("/api/budgets/{id}",budgetId).session(alice).with(csrf())).andExpect(status().isNoContent());
  mvc.perform(delete("/api/goals/{id}",goalId).session(alice).with(csrf())).andExpect(status().isNoContent());
  mvc.perform(delete("/api/recurring-transactions/{id}",recurringId).session(alice).with(csrf())).andExpect(status().isNoContent());
 }

 private MockHttpSession sessionFor(String email,String name) throws Exception {
  String register="""
   {"email":"%s","password":"a-long-test-password","displayName":"%s"}
   """.formatted(email,name);
  mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(register)).andExpect(status().isCreated());
  MvcResult login=mvc.perform(post("/api/auth/login").with(csrf()).param("email",email).param("password","a-long-test-password")).andExpect(status().isNoContent()).andReturn();
  return (MockHttpSession)login.getRequest().getSession(false);
 }
}
