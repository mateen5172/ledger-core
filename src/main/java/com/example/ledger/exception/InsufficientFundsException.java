package com.example.ledger.exception;

import java.util.UUID;

public class InsufficientFundsException extends BusinessRuleException {

    public InsufficientFundsException(UUID accountId, long available, long required) {
        super("Insufficient funds in account " + accountId
                + ": available " + available + ", required " + required + " (minor units)");
    }
}
