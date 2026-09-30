package com.example.ledger.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.example.ledger.domain.Account;
import com.example.ledger.dto.IntegrityReport;
import com.example.ledger.repository.AccountLedgerTotal;
import com.example.ledger.repository.AccountRepository;
import com.example.ledger.repository.LedgerEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reconciles the denormalised account balances against the immutable ledger. Runs in a
 * REPEATABLE_READ snapshot so concurrent postings cannot produce false alarms.
 */
@Service
public class IntegrityService {

    private final AccountRepository accountRepository;
    private final LedgerEntryRepository entryRepository;

    public IntegrityService(AccountRepository accountRepository, LedgerEntryRepository entryRepository) {
        this.accountRepository = accountRepository;
        this.entryRepository = entryRepository;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public IntegrityReport check() {
        List<Account> accounts = accountRepository.findAll();
        Map<UUID, Long> ledgerTotals = entryRepository.totalsPerAccount().stream()
                .collect(Collectors.toMap(AccountLedgerTotal::accountId, t -> t.total() == null ? 0L : t.total()));

        long totalBalance = 0;
        List<IntegrityReport.Discrepancy> discrepancies = new ArrayList<>();
        for (Account account : accounts) {
            totalBalance += account.getBalance();
            long ledgerBalance = ledgerTotals.getOrDefault(account.getId(), 0L);
            if (ledgerBalance != account.getBalance()) {
                discrepancies.add(new IntegrityReport.Discrepancy(account.getId(), account.getBalance(), ledgerBalance));
            }
        }
        List<UUID> unbalanced = entryRepository.findUnbalancedTransactionIds();

        boolean consistent = totalBalance == 0 && discrepancies.isEmpty() && unbalanced.isEmpty();
        return new IntegrityReport(consistent, accounts.size(), totalBalance, discrepancies, unbalanced);
    }
}
