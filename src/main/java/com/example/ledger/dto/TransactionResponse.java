package com.example.ledger.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.example.ledger.domain.Direction;
import com.example.ledger.domain.JournalTransaction;
import com.example.ledger.domain.TransactionType;

public record TransactionResponse(
        UUID id,
        TransactionType type,
        String idempotencyKey,
        String description,
        String currency,
        UUID reversalOf,
        Instant createdAt,
        List<Line> entries) {

    public record Line(Long entryId, UUID accountId, Direction direction, long amount, long balanceAfter) {
    }

    public static TransactionResponse from(JournalTransaction tx) {
        List<Line> lines = tx.getEntries().stream()
                .map(e -> new Line(e.getId(), e.getAccount().getId(), e.getDirection(),
                        e.getAmount(), e.getBalanceAfter()))
                .toList();
        return new TransactionResponse(tx.getId(), tx.getType(), tx.getIdempotencyKey(), tx.getDescription(),
                tx.getCurrency(), tx.getReversalOf(), tx.getCreatedAt(), lines);
    }
}
