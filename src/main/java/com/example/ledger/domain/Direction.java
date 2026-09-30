package com.example.ledger.domain;

/** DEBIT: value leaves the account (balance decreases). CREDIT: value enters it (balance increases). */
public enum Direction {
    DEBIT,
    CREDIT;

    public Direction opposite() {
        return this == DEBIT ? CREDIT : DEBIT;
    }
}
