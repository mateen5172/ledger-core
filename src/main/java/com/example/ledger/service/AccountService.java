package com.example.ledger.service;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.example.ledger.config.LedgerProperties;
import com.example.ledger.domain.Account;
import com.example.ledger.dto.AccountResponse;
import com.example.ledger.dto.CreateAccountRequest;
import com.example.ledger.dto.EntryResponse;
import com.example.ledger.dto.PageResponse;
import com.example.ledger.exception.BadRequestException;
import com.example.ledger.exception.ResourceNotFoundException;
import com.example.ledger.repository.AccountRepository;
import com.example.ledger.repository.LedgerEntryRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AccountService {

    private final AccountRepository accountRepository;
    private final LedgerEntryRepository entryRepository;
    private final LedgerProperties properties;

    public AccountService(AccountRepository accountRepository,
                          LedgerEntryRepository entryRepository,
                          LedgerProperties properties) {
        this.accountRepository = accountRepository;
        this.entryRepository = entryRepository;
        this.properties = properties;
    }

    @Transactional
    public AccountResponse create(CreateAccountRequest request) {
        if (!properties.currencies().contains(request.currency())) {
            throw new BadRequestException("Unsupported currency " + request.currency()
                    + ". Supported: " + String.join(", ", properties.currencies()));
        }
        Account account = accountRepository.save(new Account(request.ownerName().trim(), request.currency()));
        return AccountResponse.from(account);
    }

    public AccountResponse get(UUID id) {
        return AccountResponse.from(find(id));
    }

    public PageResponse<AccountResponse> listCustomerAccounts(Pageable pageable) {
        return PageResponse.of(accountRepository.findBySystemAccountFalse(pageable).map(AccountResponse::from));
    }

    public List<AccountResponse> listSystemAccounts() {
        return accountRepository.findBySystemAccountTrueOrderByCurrency().stream()
                .map(AccountResponse::from)
                .toList();
    }

    public PageResponse<EntryResponse> statement(UUID accountId, Pageable pageable) {
        if (!accountRepository.existsById(accountId)) {
            throw new ResourceNotFoundException("Account", accountId);
        }
        return PageResponse.of(entryRepository.findByAccountId(accountId, pageable).map(EntryResponse::from));
    }

    // Status changes take the same row lock as postings, so they serialise with in-flight transfers.

    @Transactional
    public AccountResponse freeze(UUID id) {
        Account account = lock(id);
        account.freeze();
        return AccountResponse.from(account);
    }

    @Transactional
    public AccountResponse unfreeze(UUID id) {
        Account account = lock(id);
        account.unfreeze();
        return AccountResponse.from(account);
    }

    @Transactional
    public AccountResponse close(UUID id) {
        Account account = lock(id);
        account.close();
        return AccountResponse.from(account);
    }

    private Account find(UUID id) {
        return accountRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Account", id));
    }

    private Account lock(UUID id) {
        List<Account> locked = accountRepository.lockAllByIdOrdered(Set.of(id));
        if (locked.isEmpty()) {
            throw new ResourceNotFoundException("Account", id);
        }
        return locked.get(0);
    }
}
