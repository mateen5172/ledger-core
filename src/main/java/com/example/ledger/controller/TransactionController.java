package com.example.ledger.controller;

import java.util.UUID;

import com.example.ledger.dto.TransactionResponse;
import com.example.ledger.service.LedgerService;
import com.example.ledger.service.PaymentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transactions")
@Tag(name = "Transactions")
public class TransactionController {

    private final LedgerService ledgerService;
    private final PaymentService paymentService;

    public TransactionController(LedgerService ledgerService, PaymentService paymentService) {
        this.ledgerService = ledgerService;
        this.paymentService = paymentService;
    }

    @GetMapping("/{id}")
    public TransactionResponse get(@PathVariable UUID id) {
        return ledgerService.get(id);
    }

    /** Posts a compensating transaction. History is never edited; calling this twice is safe. */
    @PostMapping("/{id}/reverse")
    public ResponseEntity<TransactionResponse> reverse(@PathVariable UUID id) {
        return PostingResponses.of(paymentService.reverse(id));
    }
}
