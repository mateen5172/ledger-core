package com.example.ledger.domain;

public enum AccountStatus {
    ACTIVE,
    /** Debits are blocked, credits still allowed. */
    FROZEN,
    /** No postings allowed. Only possible with a zero balance. */
    CLOSED
}
