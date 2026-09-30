package com.example.ledger.seed;

import com.example.ledger.dto.AccountResponse;
import com.example.ledger.dto.CreateAccountRequest;
import com.example.ledger.dto.MoneyRequest;
import com.example.ledger.dto.TransferRequest;
import com.example.ledger.repository.AccountRepository;
import com.example.ledger.service.AccountService;
import com.example.ledger.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/** Creates a few accounts and postings so the API is explorable immediately. Skipped in tests. */
@Component
@Profile("!test")
@Order(2)
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final AccountRepository accountRepository;
    private final AccountService accountService;
    private final PaymentService paymentService;

    public DemoDataSeeder(AccountRepository accountRepository, AccountService accountService,
                          PaymentService paymentService) {
        this.accountRepository = accountRepository;
        this.accountService = accountService;
        this.paymentService = paymentService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (accountRepository.findBySystemAccountFalse(PageRequest.of(0, 1)).hasContent()) {
            return;
        }
        AccountResponse alice = accountService.create(new CreateAccountRequest("Alice Johnson", "USD"));
        AccountResponse bob = accountService.create(new CreateAccountRequest("Bob Smith", "USD"));
        AccountResponse priya = accountService.create(new CreateAccountRequest("Priya Sharma", "INR"));

        paymentService.deposit(alice.id(), new MoneyRequest(500_00L, "Initial funding"), "seed-deposit-alice");
        paymentService.deposit(bob.id(), new MoneyRequest(250_00L, "Initial funding"), "seed-deposit-bob");
        paymentService.deposit(priya.id(), new MoneyRequest(10_000_00L, "Initial funding"), "seed-deposit-priya");
        paymentService.transfer(new TransferRequest(alice.id(), bob.id(), 75_00L, "Dinner split"), "seed-transfer-1");

        log.info("Seeded demo data: Alice {}, Bob {}, Priya {}", alice.id(), bob.id(), priya.id());
    }
}
