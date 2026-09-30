package com.example.ledger.repository;

import java.util.UUID;

/** Sum of an account's ledger entries (credits minus debits), used for reconciliation. */
public record AccountLedgerTotal(UUID accountId, Long total) {
}
