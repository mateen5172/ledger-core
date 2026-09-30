package com.example.ledger.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Currencies the ledger accepts. Each one gets a system clearing account at start-up. */
@ConfigurationProperties(prefix = "ledger")
public record LedgerProperties(@DefaultValue({"USD", "EUR", "INR"}) List<String> currencies) {
}
