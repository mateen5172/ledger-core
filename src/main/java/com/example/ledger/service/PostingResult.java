package com.example.ledger.service;

import com.example.ledger.dto.TransactionResponse;

/** {@code replayed} is true when the idempotency key had already been processed. */
public record PostingResult(TransactionResponse transaction, boolean replayed) {
}
