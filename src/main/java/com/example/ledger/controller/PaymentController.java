package com.example.ledger.controller;

import java.util.UUID;

import com.example.ledger.dto.MoneyRequest;
import com.example.ledger.dto.TransactionResponse;
import com.example.ledger.dto.TransferRequest;
import com.example.ledger.service.PaymentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Money-moving endpoints. All of them require an Idempotency-Key header: retrying with the same
 * key returns the original result (200 + Idempotent-Replayed) instead of moving money twice.
 */
@RestController
@RequestMapping("/api")
@Tag(name = "Payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/transfers")
    public ResponseEntity<TransactionResponse> transfer(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody TransferRequest request) {
        return PostingResponses.of(paymentService.transfer(request, idempotencyKey));
    }

    @PostMapping("/accounts/{accountId}/deposits")
    public ResponseEntity<TransactionResponse> deposit(
            @PathVariable UUID accountId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody MoneyRequest request) {
        return PostingResponses.of(paymentService.deposit(accountId, request, idempotencyKey));
    }

    @PostMapping("/accounts/{accountId}/withdrawals")
    public ResponseEntity<TransactionResponse> withdraw(
            @PathVariable UUID accountId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody MoneyRequest request) {
        return PostingResponses.of(paymentService.withdraw(accountId, request, idempotencyKey));
    }
}
