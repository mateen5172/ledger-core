package com.example.ledger.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import com.example.ledger.domain.Direction;
import com.example.ledger.domain.TransactionType;

/** A request to post a balanced set of legs to the ledger. */
public record PostingCommand(
        TransactionType type,
        String idempotencyKey,
        String description,
        UUID reversalOf,
        List<Leg> legs) {

    public record Leg(UUID accountId, Direction direction, long amount) {
    }

    /**
     * Stable hash of the financial content (not the description). The same idempotency key with a
     * different fingerprint means the client is misusing the key.
     */
    public String fingerprint() {
        String legsPart = legs.stream()
                .map(l -> l.accountId() + ":" + l.direction() + ":" + l.amount())
                .sorted()
                .collect(Collectors.joining(","));
        String canonical = type + "|" + reversalOf + "|" + legsPart;
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
