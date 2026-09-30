package com.example.ledger.controller;

import java.net.URI;

import com.example.ledger.dto.TransactionResponse;
import com.example.ledger.service.PostingResult;
import org.springframework.http.ResponseEntity;

final class PostingResponses {

    private PostingResponses() {
    }

    /** 201 Created for a new posting; 200 OK plus an Idempotent-Replayed header when the key was already used. */
    static ResponseEntity<TransactionResponse> of(PostingResult result) {
        TransactionResponse tx = result.transaction();
        if (result.replayed()) {
            return ResponseEntity.ok().header("Idempotent-Replayed", "true").body(tx);
        }
        return ResponseEntity.created(URI.create("/api/transactions/" + tx.id())).body(tx);
    }
}
