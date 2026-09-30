package com.example.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.example.ledger.domain.Direction;
import com.example.ledger.dto.AccountResponse;
import com.example.ledger.dto.CreateAccountRequest;
import com.example.ledger.dto.IntegrityReport;
import com.example.ledger.dto.MoneyRequest;
import com.example.ledger.dto.TransferRequest;
import com.example.ledger.exception.BusinessRuleException;
import com.example.ledger.exception.IdempotencyConflictException;
import com.example.ledger.exception.InsufficientFundsException;
import com.example.ledger.repository.JournalTransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.test.context.ActiveProfiles;

/**
 * Exercises the real posting engine against Hibernate + H2. The class-level annotations intentionally
 * match LedgerApiIntegrationTest so both share one cached Spring context (and one database).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LedgerServiceIntegrationTest {

    @Autowired
    private AccountService accountService;
    @Autowired
    private PaymentService paymentService;
    @Autowired
    private IntegrityService integrityService;
    @Autowired
    private JournalTransactionRepository transactionRepository;

    @Test
    void transferMovesMoneyAndWritesBalancedEntries() {
        AccountResponse alice = newAccount("Alice", "USD");
        AccountResponse bob = newAccount("Bob", "USD");
        deposit(alice, 10_000);

        PostingResult result = paymentService.transfer(
                new TransferRequest(alice.id(), bob.id(), 2_500L, "rent"), key());

        assertThat(result.replayed()).isFalse();
        assertThat(balance(alice)).isEqualTo(7_500);
        assertThat(balance(bob)).isEqualTo(2_500);
        assertThat(result.transaction().entries()).hasSize(2);
        assertThat(result.transaction().entries())
                .extracting(l -> l.direction())
                .containsExactlyInAnyOrder(Direction.DEBIT, Direction.CREDIT);
        assertThat(integrityService.check().consistent()).isTrue();
    }

    @Test
    void sameIdempotencyKeyReplaysWithoutPostingTwice() {
        AccountResponse alice = newAccount("Alice", "USD");
        AccountResponse bob = newAccount("Bob", "USD");
        deposit(alice, 10_000);
        String key = key();
        TransferRequest request = new TransferRequest(alice.id(), bob.id(), 1_000L, "coffee");

        PostingResult first = paymentService.transfer(request, key);
        PostingResult second = paymentService.transfer(request, key);

        assertThat(first.replayed()).isFalse();
        assertThat(second.replayed()).isTrue();
        assertThat(second.transaction().id()).isEqualTo(first.transaction().id());
        assertThat(balance(alice)).isEqualTo(9_000);
        assertThat(balance(bob)).isEqualTo(1_000);
    }

    @Test
    void reusingAKeyWithDifferentPayloadIsRejected() {
        AccountResponse alice = newAccount("Alice", "USD");
        AccountResponse bob = newAccount("Bob", "USD");
        deposit(alice, 10_000);
        String key = key();

        paymentService.transfer(new TransferRequest(alice.id(), bob.id(), 1_000L, null), key);

        assertThatThrownBy(() -> paymentService.transfer(
                new TransferRequest(alice.id(), bob.id(), 2_000L, null), key))
                .isInstanceOf(IdempotencyConflictException.class);
        assertThat(balance(alice)).isEqualTo(9_000);
    }

    @Test
    void insufficientFundsLeavesEverythingUntouched() {
        AccountResponse alice = newAccount("Alice", "USD");
        AccountResponse bob = newAccount("Bob", "USD");
        deposit(alice, 500);
        String key = key();

        assertThatThrownBy(() -> paymentService.transfer(
                new TransferRequest(alice.id(), bob.id(), 501L, null), key))
                .isInstanceOf(InsufficientFundsException.class);

        assertThat(balance(alice)).isEqualTo(500);
        assertThat(balance(bob)).isZero();
        assertThat(transactionRepository.findByIdempotencyKey(key)).isEmpty();
    }

    @Test
    void reversalRestoresBalancesAndIsIdempotent() {
        AccountResponse alice = newAccount("Alice", "USD");
        AccountResponse bob = newAccount("Bob", "USD");
        deposit(alice, 10_000);
        PostingResult transfer = paymentService.transfer(
                new TransferRequest(alice.id(), bob.id(), 4_000L, null), key());

        PostingResult reversal = paymentService.reverse(transfer.transaction().id());

        assertThat(reversal.replayed()).isFalse();
        assertThat(reversal.transaction().reversalOf()).isEqualTo(transfer.transaction().id());
        assertThat(balance(alice)).isEqualTo(10_000);
        assertThat(balance(bob)).isZero();

        PostingResult again = paymentService.reverse(transfer.transaction().id());
        assertThat(again.replayed()).isTrue();
        assertThat(balance(alice)).isEqualTo(10_000);

        assertThatThrownBy(() -> paymentService.reverse(reversal.transaction().id()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("cannot itself be reversed");
        assertThat(integrityService.check().consistent()).isTrue();
    }

    @Test
    void reversalFailsIfReceiverAlreadySpentTheMoney() {
        AccountResponse alice = newAccount("Alice", "USD");
        AccountResponse bob = newAccount("Bob", "USD");
        AccountResponse carol = newAccount("Carol", "USD");
        deposit(alice, 1_000);
        PostingResult transfer = paymentService.transfer(
                new TransferRequest(alice.id(), bob.id(), 1_000L, null), key());
        paymentService.transfer(new TransferRequest(bob.id(), carol.id(), 1_000L, null), key());

        assertThatThrownBy(() -> paymentService.reverse(transfer.transaction().id()))
                .isInstanceOf(InsufficientFundsException.class);
    }

    @Test
    void transferBetweenDifferentCurrenciesIsRejected() {
        AccountResponse usd = newAccount("Alice", "USD");
        AccountResponse eur = newAccount("Bob", "EUR");
        deposit(usd, 1_000);

        assertThatThrownBy(() -> paymentService.transfer(
                new TransferRequest(usd.id(), eur.id(), 100L, null), key()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("same currency");
    }

    @Test
    void frozenAccountCannotSendButCanReceive() {
        AccountResponse alice = newAccount("Alice", "USD");
        AccountResponse bob = newAccount("Bob", "USD");
        deposit(alice, 1_000);
        deposit(bob, 1_000);
        accountService.freeze(alice.id());

        assertThatThrownBy(() -> paymentService.transfer(
                new TransferRequest(alice.id(), bob.id(), 100L, null), key()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("frozen");

        paymentService.transfer(new TransferRequest(bob.id(), alice.id(), 100L, null), key());
        assertThat(balance(alice)).isEqualTo(1_100);
    }

    @Test
    void withdrawalCannotExceedBalance() {
        AccountResponse alice = newAccount("Alice", "USD");
        deposit(alice, 1_000);

        paymentService.withdraw(alice.id(), new MoneyRequest(400L, null), key());
        assertThat(balance(alice)).isEqualTo(600);

        assertThatThrownBy(() -> paymentService.withdraw(alice.id(), new MoneyRequest(601L, null), key()))
                .isInstanceOf(InsufficientFundsException.class);
    }

    /**
     * Opposing transfers between the same two accounts are the classic deadlock scenario. Deterministic
     * lock ordering plus optimistic versioning must keep every cent accounted for.
     */
    @Test
    void concurrentOpposingTransfersConserveMoneyWithoutLostUpdates() throws Exception {
        AccountResponse a = newAccount("Account A", "USD");
        AccountResponse b = newAccount("Account B", "USD");
        deposit(a, 100_000);
        deposit(b, 100_000);

        int threads = 8;
        int transfersPerThread = 25;
        long amount = 10;
        AtomicInteger aToB = new AtomicInteger();
        AtomicInteger bToA = new AtomicInteger();

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            final int threadNo = t;
            futures.add(pool.submit(() -> {
                for (int i = 0; i < transfersPerThread; i++) {
                    boolean forward = (threadNo + i) % 2 == 0;
                    UUID from = forward ? a.id() : b.id();
                    UUID to = forward ? b.id() : a.id();
                    transferWithRetry(from, to, amount, key());
                    (forward ? aToB : bToA).incrementAndGet();
                }
            }));
        }
        for (Future<?> future : futures) {
            future.get(90, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertThat(aToB.get() + bToA.get()).isEqualTo(threads * transfersPerThread);
        assertThat(balance(a)).isEqualTo(100_000 - amount * aToB.get() + amount * bToA.get());
        assertThat(balance(b)).isEqualTo(100_000 + amount * aToB.get() - amount * bToA.get());
        assertThat(balance(a) + balance(b)).isEqualTo(200_000);

        IntegrityReport report = integrityService.check();
        assertThat(report.discrepancies()).isEmpty();
        assertThat(report.unbalancedTransactions()).isEmpty();
        assertThat(report.consistent()).isTrue();
    }

    // ----- helpers -----

    /** Retries with the SAME idempotency key, exactly as a well-behaved client would. */
    private void transferWithRetry(UUID from, UUID to, long amount, String key) {
        for (int attempt = 0; attempt < 50; attempt++) {
            try {
                paymentService.transfer(new TransferRequest(from, to, amount, "concurrent"), key);
                return;
            } catch (ConcurrencyFailureException ex) {
                sleepQuietly(5L * (attempt + 1));
            }
        }
        throw new AssertionError("Transfer kept failing due to contention");
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private AccountResponse newAccount(String owner, String currency) {
        return accountService.create(new CreateAccountRequest(owner, currency));
    }

    private void deposit(AccountResponse account, long amount) {
        paymentService.deposit(account.id(), new MoneyRequest(amount, "test funding"), key());
    }

    private long balance(AccountResponse account) {
        return accountService.get(account.id()).balance();
    }

    private String key() {
        return UUID.randomUUID().toString();
    }
}
