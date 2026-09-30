package com.example.ledger.dto;

import java.time.Instant;
import java.util.UUID;

import com.example.ledger.domain.Direction;
import com.example.ledger.domain.LedgerEntry;
import com.example.ledger.domain.TransactionType;

/** One line of an account statement. */
public record EntryResponse(
        Long id,
        UUID transactionId,
        TransactionType type,
        String description,
        Direction direction,
        long amount,
        long balanceAfter,
        Instant createdAt) {

    public static EntryResponse from(LedgerEntry e) {
        return new EntryResponse(e.getId(), e.getTransaction().getId(), e.getTransaction().getType(),
                e.getTransaction().getDescription(), e.getDirection(), e.getAmount(),
                e.getBalanceAfter(), e.getCreatedAt());
    }
}
