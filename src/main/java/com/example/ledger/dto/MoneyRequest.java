package com.example.ledger.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Body of a deposit or withdrawal. {@code amount} is in minor units. */
public record MoneyRequest(
        @NotNull @Positive @Max(1_000_000_000_000L) Long amount,
        @Size(max = 255) String description) {
}
