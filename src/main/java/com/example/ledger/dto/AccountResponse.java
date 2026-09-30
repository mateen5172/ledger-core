package com.example.ledger.dto;

import java.time.Instant;
import java.util.UUID;

import com.example.ledger.domain.Account;
import com.example.ledger.domain.AccountStatus;

/** Balance is expressed in minor units (2500 = 25.00). */
public record AccountResponse(
        UUID id,
        String ownerName,
        String currency,
        long balance,
        AccountStatus status,
        boolean systemAccount,
        Instant createdAt) {

    public static AccountResponse from(Account account) {
        return new AccountResponse(account.getId(), account.getOwnerName(), account.getCurrency(),
                account.getBalance(), account.getStatus(), account.isSystemAccount(), account.getCreatedAt());
    }
}
