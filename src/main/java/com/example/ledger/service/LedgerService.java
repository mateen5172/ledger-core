package com.example.ledger.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.example.ledger.domain.Account;
import com.example.ledger.domain.JournalTransaction;
import com.example.ledger.domain.LedgerEntry;
import com.example.ledger.domain.TransactionType;
import com.example.ledger.dto.TransactionResponse;
import com.example.ledger.exception.BadRequestException;
import com.example.ledger.exception.BusinessRuleException;
import com.example.ledger.exception.IdempotencyConflictException;
import com.example.ledger.exception.ResourceNotFoundException;
import com.example.ledger.repository.AccountRepository;
import com.example.ledger.repository.JournalTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The posting engine. Everything that moves money goes through {@link #post(PostingCommand)}, which
 * runs in a single database transaction:
 * <ol>
 *   <li>lock all involved accounts in a deterministic order (deadlock-free),</li>
 *   <li>look up the idempotency key (replay or reject a mismatching payload),</li>
 *   <li>apply each leg to the account balance and record an immutable ledger entry,</li>
 *   <li>assert that debits equal credits, then commit.</li>
 * </ol>
 */
@Service
@Transactional(readOnly = true)
public class LedgerService {

    private final AccountRepository accountRepository;
    private final JournalTransactionRepository transactionRepository;

    public LedgerService(AccountRepository accountRepository,
                         JournalTransactionRepository transactionRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public PostingResult post(PostingCommand command) {
        validate(command);

        Set<UUID> accountIds = command.legs().stream()
                .map(PostingCommand.Leg::accountId)
                .collect(Collectors.toSet());
        List<Account> accounts = accountRepository.lockAllByIdOrdered(accountIds);
        if (accounts.size() != accountIds.size()) {
            Set<UUID> found = accounts.stream().map(Account::getId).collect(Collectors.toSet());
            UUID missing = accountIds.stream().filter(id -> !found.contains(id)).findFirst().orElseThrow();
            throw new ResourceNotFoundException("Account", missing);
        }

        // Checked *after* taking the locks: a concurrent request with the same key waits here and then sees the result.
        Optional<JournalTransaction> existing = transactionRepository.findByIdempotencyKey(command.idempotencyKey());
        if (existing.isPresent()) {
            JournalTransaction tx = existing.get();
            if (!tx.getRequestFingerprint().equals(command.fingerprint())) {
                throw new IdempotencyConflictException(command.idempotencyKey());
            }
            return new PostingResult(TransactionResponse.from(tx), true);
        }

        String currency = accounts.get(0).getCurrency();
        if (accounts.stream().anyMatch(a -> !a.getCurrency().equals(currency))) {
            throw new BusinessRuleException(
                    "All accounts in a transaction must use the same currency (no FX support)");
        }

        Map<UUID, Account> byId = accounts.stream()
                .collect(Collectors.toMap(Account::getId, Function.identity()));
        JournalTransaction tx = new JournalTransaction(command.type(), command.idempotencyKey(),
                command.fingerprint(), command.description(), command.reversalOf(), currency);

        for (PostingCommand.Leg leg : command.legs()) {
            Account account = byId.get(leg.accountId());
            account.apply(leg.direction(), leg.amount());
            tx.addEntry(new LedgerEntry(tx, account, leg.direction(), leg.amount(), account.getBalance()));
        }
        tx.assertBalanced();

        transactionRepository.saveAndFlush(tx);
        return new PostingResult(TransactionResponse.from(tx), false);
    }

    /**
     * Reverses a transaction by posting the mirror-image legs. The key is derived from the original id,
     * so calling this repeatedly is naturally idempotent.
     */
    @Transactional
    public PostingResult reverse(UUID transactionId) {
        JournalTransaction original = transactionRepository.findWithEntriesById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", transactionId));
        if (original.getType() == TransactionType.REVERSAL) {
            throw new BusinessRuleException("A reversal transaction cannot itself be reversed");
        }
        List<PostingCommand.Leg> legs = original.getEntries().stream()
                .map(e -> new PostingCommand.Leg(e.getAccount().getId(), e.getDirection().opposite(), e.getAmount()))
                .toList();
        return post(new PostingCommand(TransactionType.REVERSAL, "reversal-" + transactionId,
                "Reversal of " + transactionId, transactionId, legs));
    }

    public TransactionResponse get(UUID transactionId) {
        return transactionRepository.findWithEntriesById(transactionId)
                .map(TransactionResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", transactionId));
    }

    private void validate(PostingCommand command) {
        if (command.idempotencyKey() == null || command.idempotencyKey().isBlank()
                || command.idempotencyKey().length() > 100) {
            throw new BadRequestException("Idempotency-Key must be between 1 and 100 characters");
        }
        if (command.legs() == null || command.legs().size() < 2) {
            throw new BadRequestException("A transaction needs at least two legs");
        }
        if (command.legs().stream().anyMatch(l -> l.amount() <= 0)) {
            throw new BadRequestException("All amounts must be positive");
        }
    }
}
