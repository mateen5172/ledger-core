package com.example.ledger.controller;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import com.example.ledger.dto.AccountResponse;
import com.example.ledger.dto.CreateAccountRequest;
import com.example.ledger.dto.EntryResponse;
import com.example.ledger.dto.PageResponse;
import com.example.ledger.service.AccountService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/accounts")
@Tag(name = "Accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    public ResponseEntity<AccountResponse> create(@Valid @RequestBody CreateAccountRequest request) {
        AccountResponse created = accountService.create(request);
        return ResponseEntity.created(URI.create("/api/accounts/" + created.id())).body(created);
    }

    @GetMapping
    public PageResponse<AccountResponse> list(
            @ParameterObject
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return accountService.listCustomerAccounts(pageable);
    }

    /** Internal clearing accounts; their (negative) balances mirror the money deposited into the system. */
    @GetMapping("/system")
    public List<AccountResponse> systemAccounts() {
        return accountService.listSystemAccounts();
    }

    @GetMapping("/{id}")
    public AccountResponse get(@PathVariable UUID id) {
        return accountService.get(id);
    }

    @GetMapping("/{id}/entries")
    public PageResponse<EntryResponse> statement(
            @PathVariable UUID id,
            @ParameterObject
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return accountService.statement(id, pageable);
    }

    @PostMapping("/{id}/freeze")
    public AccountResponse freeze(@PathVariable UUID id) {
        return accountService.freeze(id);
    }

    @PostMapping("/{id}/unfreeze")
    public AccountResponse unfreeze(@PathVariable UUID id) {
        return accountService.unfreeze(id);
    }

    @PostMapping("/{id}/close")
    public AccountResponse close(@PathVariable UUID id) {
        return accountService.close(id);
    }
}