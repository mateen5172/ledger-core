package com.example.ledger.exception;

/** A well-formed request that violates a ledger rule (frozen account, currency mismatch, ...). */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
