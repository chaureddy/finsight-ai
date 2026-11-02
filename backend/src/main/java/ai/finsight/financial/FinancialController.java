package ai.finsight.financial;

import ai.finsight.user.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api")
public class FinancialController {
    private static final String ASSET = "financial_asset";
    private static final String LIABILITY = "financial_liability";
    private static final String INVESTMENT = "financial_investment";
    private static final String INSURANCE = "insurance_policy";
    private final NamedParameterJdbcTemplate jdbc;
    private final UserRepository users;

    public FinancialController(NamedParameterJdbcTemplate jdbc, UserRepository users) {
        this.jdbc = jdbc;
        this.users = users;
    }

    public record AssetInput(@NotBlank @Size(max=120) String name,
        @NotBlank @Pattern(regexp="Cash|Bank Account|Property|Vehicle|Gold|Other") String category,
        @NotNull @DecimalMin("0.00") @Digits(integer=17,fraction=2) BigDecimal currentValue,
        @NotNull @DecimalMin("0.00") @Digits(integer=17,fraction=2) BigDecimal purchaseValue,
        @NotNull @PastOrPresent LocalDate purchaseDate, @Size(max=1000) String description) {}
    public record LiabilityInput(@NotBlank @Size(max=120) String name,
        @NotBlank @Pattern(regexp="Personal Loan|Home Loan|Vehicle Loan|Education Loan|Credit Card|Other") String category,
        @NotNull @DecimalMin("0.00") @Digits(integer=17,fraction=2) BigDecimal principalAmount,
        @NotNull @DecimalMin("0.00") @Digits(integer=17,fraction=2) BigDecimal outstandingAmount,
        @NotNull @DecimalMin("0.0000") @DecimalMax("100.0000") @Digits(integer=3,fraction=4) BigDecimal interestRate,
        @NotNull @DecimalMin("0.00") @Digits(integer=17,fraction=2) BigDecimal monthlyEmi,
        @NotNull @PastOrPresent LocalDate startDate, LocalDate dueDate, @Size(max=1000) String description) {}
    public record InvestmentInput(@NotBlank @Size(max=120) String name,
        @NotBlank @Pattern(regexp="Stocks|Mutual Funds|Fixed Deposits|Bonds|Gold|Other") String category,
        @NotNull @DecimalMin("0.00") @Digits(integer=17,fraction=2) BigDecimal investedAmount,
        @NotNull @DecimalMin("0.00") @Digits(integer=17,fraction=2) BigDecimal currentValue,
        @NotNull @PastOrPresent LocalDate purchaseDate, @Size(max=1000) String description) {}
    public record InsuranceInput(@NotBlank @Size(max=120) String provider,
        @NotBlank @Size(max=120) String policyName,
        @NotBlank @Pattern(regexp="Health|Life|Vehicle|Property|Other") String category,
        @NotNull @DecimalMin("0.00") @Digits(integer=17,fraction=2) BigDecimal coverageAmount,
        @NotNull @DecimalMin("0.00") @Digits(integer=17,fraction=2) BigDecimal premiumAmount,
        @NotBlank @Pattern(regexp="Monthly|Quarterly|Semiannual|Annual") String premiumFrequency,
        @NotNull @PastOrPresent LocalDate startDate, LocalDate renewalDate, @Size(max=1000) String description) {}

    @GetMapping("/assets") public List<Map<String,Object>> assets(Authentication auth) { return list(ASSET, owner(auth)); }
    @PostMapping("/assets") @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> createAsset(@Valid @RequestBody AssetInput in, Authentication auth) {
        UUID id=UUID.randomUUID(), user=owner(auth);
        jdbc.update("INSERT INTO financial_asset(id,user_id,name,category,current_value,purchase_value,purchase_date,description) VALUES(:id,:user,:name,:category,:current,:purchase,:date,:description)", params("id",id,"user",user,"name",in.name().trim(),"category",in.category(),"current",in.currentValue(),"purchase",in.purchaseValue(),"date",in.purchaseDate(),"description",in.description()));
        return find(ASSET,id,user);
    }
    @PutMapping("/assets/{id}") public Map<String,Object> updateAsset(@PathVariable UUID id,@Valid @RequestBody AssetInput in,Authentication auth) {
        UUID user=owner(auth); update(ASSET,id,user,"UPDATE financial_asset SET name=:name,category=:category,current_value=:current,purchase_value=:purchase,purchase_date=:date,description=:description,updated_at=now() WHERE id=:id AND user_id=:user",params("name",in.name().trim(),"category",in.category(),"current",in.currentValue(),"purchase",in.purchaseValue(),"date",in.purchaseDate(),"description",in.description())); return find(ASSET,id,user);
    }
    @DeleteMapping("/assets/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteAsset(@PathVariable UUID id,Authentication auth){delete(ASSET,id,owner(auth));}

    @GetMapping("/liabilities") public List<Map<String,Object>> liabilities(Authentication auth){return list(LIABILITY,owner(auth));}
    @PostMapping("/liabilities") @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> createLiability(@Valid @RequestBody LiabilityInput in,Authentication auth){UUID id=UUID.randomUUID(),user=owner(auth);jdbc.update("INSERT INTO financial_liability(id,user_id,name,category,principal_amount,outstanding_amount,interest_rate,monthly_emi,start_date,due_date,description) VALUES(:id,:user,:name,:category,:principal,:outstanding,:rate,:emi,:start,:due,:description)",params("id",id,"user",user,"name",in.name().trim(),"category",in.category(),"principal",in.principalAmount(),"outstanding",in.outstandingAmount(),"rate",in.interestRate(),"emi",in.monthlyEmi(),"start",in.startDate(),"due",in.dueDate(),"description",in.description()));return find(LIABILITY,id,user);}
    @PutMapping("/liabilities/{id}") public Map<String,Object> updateLiability(@PathVariable UUID id,@Valid @RequestBody LiabilityInput in,Authentication auth){UUID user=owner(auth);update(LIABILITY,id,user,"UPDATE financial_liability SET name=:name,category=:category,principal_amount=:principal,outstanding_amount=:outstanding,interest_rate=:rate,monthly_emi=:emi,start_date=:start,due_date=:due,description=:description,updated_at=now() WHERE id=:id AND user_id=:user",params("name",in.name().trim(),"category",in.category(),"principal",in.principalAmount(),"outstanding",in.outstandingAmount(),"rate",in.interestRate(),"emi",in.monthlyEmi(),"start",in.startDate(),"due",in.dueDate(),"description",in.description()));return find(LIABILITY,id,user);}
    @DeleteMapping("/liabilities/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteLiability(@PathVariable UUID id,Authentication auth){delete(LIABILITY,id,owner(auth));}

    @GetMapping("/investments") public List<Map<String,Object>> investments(Authentication auth){return list(INVESTMENT,owner(auth));}
    @PostMapping("/investments") @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> createInvestment(@Valid @RequestBody InvestmentInput in,Authentication auth){UUID id=UUID.randomUUID(),user=owner(auth);jdbc.update("INSERT INTO financial_investment(id,user_id,name,category,invested_amount,current_value,purchase_date,description) VALUES(:id,:user,:name,:category,:invested,:current,:date,:description)",params("id",id,"user",user,"name",in.name().trim(),"category",in.category(),"invested",in.investedAmount(),"current",in.currentValue(),"date",in.purchaseDate(),"description",in.description()));return find(INVESTMENT,id,user);}
    @PutMapping("/investments/{id}") public Map<String,Object> updateInvestment(@PathVariable UUID id,@Valid @RequestBody InvestmentInput in,Authentication auth){UUID user=owner(auth);update(INVESTMENT,id,user,"UPDATE financial_investment SET name=:name,category=:category,invested_amount=:invested,current_value=:current,purchase_date=:date,description=:description,updated_at=now() WHERE id=:id AND user_id=:user",params("name",in.name().trim(),"category",in.category(),"invested",in.investedAmount(),"current",in.currentValue(),"date",in.purchaseDate(),"description",in.description()));return find(INVESTMENT,id,user);}
    @DeleteMapping("/investments/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteInvestment(@PathVariable UUID id,Authentication auth){delete(INVESTMENT,id,owner(auth));}

    @GetMapping("/insurance") public List<Map<String,Object>> policies(Authentication auth){return list(INSURANCE,owner(auth));}
    @PostMapping("/insurance") @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> createPolicy(@Valid @RequestBody InsuranceInput in,Authentication auth){UUID id=UUID.randomUUID(),user=owner(auth);jdbc.update("INSERT INTO insurance_policy(id,user_id,provider,policy_name,category,coverage_amount,premium_amount,premium_frequency,start_date,renewal_date,description) VALUES(:id,:user,:provider,:policy,:category,:coverage,:premium,:frequency,:start,:renewal,:description)",params("id",id,"user",user,"provider",in.provider().trim(),"policy",in.policyName().trim(),"category",in.category(),"coverage",in.coverageAmount(),"premium",in.premiumAmount(),"frequency",in.premiumFrequency(),"start",in.startDate(),"renewal",in.renewalDate(),"description",in.description()));return find(INSURANCE,id,user);}
    @PutMapping("/insurance/{id}") public Map<String,Object> updatePolicy(@PathVariable UUID id,@Valid @RequestBody InsuranceInput in,Authentication auth){UUID user=owner(auth);update(INSURANCE,id,user,"UPDATE insurance_policy SET provider=:provider,policy_name=:policy,category=:category,coverage_amount=:coverage,premium_amount=:premium,premium_frequency=:frequency,start_date=:start,renewal_date=:renewal,description=:description,updated_at=now() WHERE id=:id AND user_id=:user",params("provider",in.provider().trim(),"policy",in.policyName().trim(),"category",in.category(),"coverage",in.coverageAmount(),"premium",in.premiumAmount(),"frequency",in.premiumFrequency(),"start",in.startDate(),"renewal",in.renewalDate(),"description",in.description()));return find(INSURANCE,id,user);}
    @DeleteMapping("/insurance/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deletePolicy(@PathVariable UUID id,Authentication auth){delete(INSURANCE,id,owner(auth));}

    @GetMapping("/financial-overview") public Map<String,Object> overview(Authentication auth){
        UUID user=owner(auth); LocalDate from=LocalDate.now().withDayOfMonth(1),to=from.plusMonths(1);
        BigDecimal assets=sum("SELECT COALESCE(SUM(current_value),0) FROM financial_asset WHERE user_id=:user",user);
        BigDecimal investments=sum("SELECT COALESCE(SUM(current_value),0) FROM financial_investment WHERE user_id=:user",user);
        BigDecimal liabilities=sum("SELECT COALESCE(SUM(outstanding_amount),0) FROM financial_liability WHERE user_id=:user",user);
        BigDecimal emi=sum("SELECT COALESCE(SUM(monthly_emi),0) FROM financial_liability WHERE user_id=:user",user);
        BigDecimal coverage=sum("SELECT COALESCE(SUM(coverage_amount),0) FROM insurance_policy WHERE user_id=:user",user);
        BigDecimal income=monthlyTransactionSum(user,from,to,"INCOME"), expenses=monthlyTransactionSum(user,from,to,"EXPENSE");
        BigDecimal savings=income.subtract(expenses).setScale(2,RoundingMode.HALF_UP);
        BigDecimal rate=income.signum()==0?BigDecimal.ZERO.setScale(2):savings.multiply(BigDecimal.valueOf(100)).divide(income,2,RoundingMode.HALF_UP);
        Map<String,Object> result=new LinkedHashMap<>();result.put("totalAssets",assets);result.put("totalInvestments",investments);result.put("totalLiabilities",liabilities);result.put("netWorth",assets.add(investments).subtract(liabilities).setScale(2,RoundingMode.HALF_UP));result.put("monthlyEmiCommitment",emi);result.put("totalInsuranceCoverage",coverage);result.put("monthlyIncome",income);result.put("monthlyExpenses",expenses);result.put("monthlySavings",savings);result.put("savingsRatePercent",rate);return result;
    }

    private BigDecimal monthlyTransactionSum(UUID user,LocalDate from,LocalDate to,String direction){return jdbc.queryForObject("SELECT COALESCE(SUM(amount),0) FROM financial_transaction WHERE user_id=:user AND occurred_on>=:from AND occurred_on<:to AND direction=:direction",params("user",user,"from",from,"to",to,"direction",direction),BigDecimal.class).setScale(2,RoundingMode.HALF_UP);}
    private BigDecimal sum(String sql,UUID user){return jdbc.queryForObject(sql,params("user",user),BigDecimal.class).setScale(2,RoundingMode.HALF_UP);}
    private UUID owner(Authentication auth){return users.findByEmailIgnoreCase(auth.getName()).orElseThrow().getId();}
    private List<Map<String,Object>> list(String table,UUID user){return jdbc.queryForList("SELECT * FROM "+table+" WHERE user_id=:user ORDER BY created_at DESC",params("user",user)).stream().map(this::apiNames).toList();}
    private Map<String,Object> find(String table,UUID id,UUID user){List<Map<String,Object>> rows=jdbc.queryForList("SELECT * FROM "+table+" WHERE id=:id AND user_id=:user",params("id",id,"user",user));if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND);return apiNames(rows.get(0));}
    private Map<String,Object> apiNames(Map<String,Object> row){Map<String,Object> result=new LinkedHashMap<>();for(var entry:row.entrySet()){if(entry.getKey().equalsIgnoreCase("user_id"))continue;StringBuilder key=new StringBuilder();boolean upper=false;for(char c:entry.getKey().toLowerCase(Locale.ROOT).toCharArray()){if(c=='_'){upper=true;}else{key.append(upper?Character.toUpperCase(c):c);upper=false;}}result.put(key.toString(),entry.getValue());}return result;}
    private void update(String table,UUID id,UUID user,String sql,MapSqlParameterSource values){values.addValue("id",id).addValue("user",user);if(jdbc.update(sql,values)==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND);}
    private void delete(String table,UUID id,UUID user){if(jdbc.update("DELETE FROM "+table+" WHERE id=:id AND user_id=:user",params("id",id,"user",user))==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND);}
    private MapSqlParameterSource params(Object... values){MapSqlParameterSource p=new MapSqlParameterSource();for(int i=0;i<values.length;i+=2)p.addValue((String)values[i],values[i+1]);return p;}
}
