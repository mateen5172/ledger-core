package com.example.ledger.dto;

import java.util.List;
import java.util.UUID;

/**
 * Result of reconciling the whole ledger. {@code consistent} is true only if
 * (1) all balances sum to zero, (2) every cached balance equals its ledger entries and
 * (3) every transaction is balanced.
 */
public record IntegrityReport(
        boolean consistent,
        int accountsChecked,
        long totalBalance,
        List<Discrepancy> discrepancies,
        List<UUID> unbalancedTransactions) {

    public record Discrepancy(UUID accountId, long cachedBalance, long ledgerBalance) {
    }
}
