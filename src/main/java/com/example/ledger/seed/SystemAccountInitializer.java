package com.example.ledger.seed;

import com.example.ledger.config.LedgerProperties;
import com.example.ledger.domain.Account;
import com.example.ledger.repository.AccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Ensures every supported currency has a system clearing account (needed for deposits/withdrawals). */
@Component
@Order(1)
public class SystemAccountInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SystemAccountInitializer.class);

    private final AccountRepository accountRepository;
    private final LedgerProperties properties;

    public SystemAccountInitializer(AccountRepository accountRepository, LedgerProperties properties) {
        this.accountRepository = accountRepository;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (String currency : properties.currencies()) {
            if (!accountRepository.existsBySystemAccountTrueAndCurrency(currency)) {
                accountRepository.save(Account.clearing(currency));
                log.info("Created clearing account for {}", currency);
            }
        }
    }
}
