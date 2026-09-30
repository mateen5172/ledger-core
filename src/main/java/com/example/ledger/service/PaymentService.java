package com.example.ledger.service;

import java.util.List;
import java.util.UUID;

import com.example.ledger.domain.Account;
import com.example.ledger.domain.Direction;
import com.example.ledger.domain.TransactionType;
import com.example.ledger.dto.MoneyRequest;
import com.example.ledger.dto.TransferRequest;
import com.example.ledger.exception.BusinessRuleException;
import com.example.ledger.exception.ResourceNotFoundException;
import com.example.ledger.repository.AccountRepository;
import org.springframework.stereotype.Service;

/** Business-level operations (transfer, deposit, withdrawal) expressed as ledger postings. */
@Service
public class PaymentService {

    private final LedgerService ledgerService;
    private final AccountRepository accountRepository;

    public PaymentService(LedgerService ledgerService, AccountRepository accountRepository) {
        this.ledgerService = ledgerService;
        this.accountRepository = accountRepository;
    }

    public PostingResult transfer(TransferRequest request, String idempotencyKey) {
        if (request.fromAccountId().equals(request.toAccountId())) {
            throw new BusinessRuleException("Source and destination accounts must differ");
        }
        long amount = request.amount();
        return ledgerService.post(new PostingCommand(TransactionType.TRANSFER, idempotencyKey,
                request.description(), null,
                List.of(new PostingCommand.Leg(request.fromAccountId(), Direction.DEBIT, amount),
                        new PostingCommand.Leg(request.toAccountId(), Direction.CREDIT, amount))));
    }

    /** Money enters the system: debit the clearing account, credit the customer. */
    public PostingResult deposit(UUID accountId, MoneyRequest request, String idempotencyKey) {
        Account account = customerAccount(accountId);
        Account clearing = clearingAccountFor(account.getCurrency());
        long amount = request.amount();
        return ledgerService.post(new PostingCommand(TransactionType.DEPOSIT, idempotencyKey,
                request.description(), null,
                List.of(new PostingCommand.Leg(clearing.getId(), Direction.DEBIT, amount),
                        new PostingCommand.Leg(accountId, Direction.CREDIT, amount))));
    }

    /** Money leaves the system: debit the customer, credit the clearing account. */
    public PostingResult withdraw(UUID accountId, MoneyRequest request, String idempotencyKey) {
        Account account = customerAccount(accountId);
        Account clearing = clearingAccountFor(account.getCurrency());
        long amount = request.amount();
        return ledgerService.post(new PostingCommand(TransactionType.WITHDRAWAL, idempotencyKey,
                request.description(), null,
                List.of(new PostingCommand.Leg(accountId, Direction.DEBIT, amount),
                        new PostingCommand.Leg(clearing.getId(), Direction.CREDIT, amount))));
    }

    public PostingResult reverse(UUID transactionId) {
        return ledgerService.reverse(transactionId);
    }

    private Account customerAccount(UUID accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountId));
        if (account.isSystemAccount()) {
            throw new BusinessRuleException("Deposits and withdrawals apply to customer accounts only");
        }
        return account;
    }

    private Account clearingAccountFor(String currency) {
        return accountRepository.findBySystemAccountTrueAndCurrency(currency)
                .orElseThrow(() -> new IllegalStateException("No clearing account for " + currency));
    }
}
