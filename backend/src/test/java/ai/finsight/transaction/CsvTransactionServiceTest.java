package ai.finsight.transaction;

import ai.finsight.account.UserAccountConnectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CsvTransactionServiceTest {
    private TransactionRepository transactions;
    private UserAccountConnectionRepository accounts;
    private CsvTransactionService service;
    private final UUID ownerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        transactions = mock(TransactionRepository.class);
        accounts = mock(UserAccountConnectionRepository.class);
        service = new CsvTransactionService(transactions, accounts);
    }

    @Test
    void importsStandardCsvWithDeduplication() {
        String csv = """
                Date,Description,Amount,Type,Category
                2026-10-15,Supermarket Groceries,2500.50,EXPENSE,Groceries
                2026-10-16,Client Payment,15000.00,INCOME,Freelance
                2026-10-15,Supermarket Groceries,2500.50,EXPENSE,Groceries
                """;

        when(transactions.existsByUserIdAndSourceProviderAndExternalReference(eq(ownerId), eq("CSV_IMPORT"), anyString()))
                .thenReturn(false);
        when(accounts.findByUserIdAndProviderAndAccountName(eq(ownerId), anyString(), anyString()))
                .thenReturn(Optional.empty());

        var result = service.importCsv(ownerId, csv, "My Bank");
        assertEquals(2, result.importedCount());
        assertEquals(1, result.duplicateCount()); // Second identical line in batch is flagged duplicate
        verify(transactions, times(2)).save(any(FinancialTransaction.class));
    }

    @Test
    void importsPayPalStyleCsv() {
        String csv = """
                "Date","Time","TimeZone","Name","Type","Status","Currency","Gross","Fee","Net","Transaction ID"
                "10/20/2026","08:30:00","GMT","Freelance Client","Payment Received","Completed","INR","12000.00","0.00","12000.00","9XX1234567890ABCD"
                """;

        when(transactions.existsByUserIdAndSourceProviderAndExternalReference(eq(ownerId), eq("CSV_IMPORT"), eq("9XX1234567890ABCD")))
                .thenReturn(false);
        when(accounts.findByUserIdAndProviderAndAccountName(eq(ownerId), anyString(), anyString()))
                .thenReturn(Optional.empty());

        var result = service.importCsv(ownerId, csv, "PayPal Statement");
        assertEquals(1, result.importedCount());
        verify(transactions, times(1)).save(argThat(tx ->
                tx.getAmount().compareTo(new BigDecimal("12000.00")) == 0 &&
                tx.getDirection() == FinancialTransaction.Direction.INCOME &&
                "9XX1234567890ABCD".equals(tx.getExternalReference())
        ));
    }

    @Test
    void exportsCsvProperlyFormatted() {
        FinancialTransaction tx1 = new FinancialTransaction(ownerId, "Salary", "Income", new BigDecimal("75000.00"),
                FinancialTransaction.Direction.INCOME, LocalDate.of(2026, 10, 1), "MANUAL", null, "INR");
        FinancialTransaction tx2 = new FinancialTransaction(ownerId, "Rent, Oct", "Housing", new BigDecimal("20000.00"),
                FinancialTransaction.Direction.EXPENSE, LocalDate.of(2026, 10, 2), "CSV_IMPORT", "REF123", "INR");

        when(transactions.findAllByUserIdOrderByOccurredOnDesc(ownerId)).thenReturn(List.of(tx1, tx2));

        String exported = service.exportCsv(ownerId);
        assertTrue(exported.startsWith("ID,Date,Description,Category,Type,Amount,Currency,Source,Reference"));
        assertTrue(exported.contains("75000.00"));
        assertTrue(exported.contains("\"Rent, Oct\"")); // Commas escaped in quotes
        assertTrue(exported.contains("REF123"));
    }
}

