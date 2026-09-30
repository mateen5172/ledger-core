package com.example.ledger.exception;

/** The same Idempotency-Key was reused with a different request payload. */
public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException(String key) {
        super("Idempotency-Key '" + key + "' was already used with a different request");
    }
}
