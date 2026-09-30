package com.example.ledger.domain;

import java.time.Instant;
import java.util.UUID;

import com.example.ledger.exception.BusinessRuleException;
import com.example.ledger.exception.InsufficientFundsException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.CreationTimestamp;

/**
 * A ledger account. {@code balance} (minor units) is a denormalised cache of the sum of its ledger
 * entries; the ledger is the source of truth and {@code IntegrityService} reconciles the two.
 * The CHECK constraint makes the database itself refuse an overdraft on customer accounts.
 */
@Entity
@Table(name = "accounts")
@Check(constraints = "balance >= 0 OR allow_negative = true")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 120)
    private String ownerName;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    private long balance;

    @Column(nullable = false)
    private boolean allowNegative;

    @Column(nullable = false)
    private boolean systemAccount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AccountStatus status = AccountStatus.ACTIVE;

    /** Safety net on top of pessimistic locking: a stale write can never silently overwrite a balance. */
    @Version
    private Long version;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    protected Account() {
        // required by JPA
    }

    public Account(String ownerName, String currency) {
        this.ownerName = ownerName;
        this.currency = currency;
    }

    /** Internal counter-party account for a currency; may go negative (it mirrors money entering/leaving the system). */
    public static Account clearing(String currency) {
        Account account = new Account("SYSTEM CLEARING " + currency, currency);
        account.allowNegative = true;
        account.systemAccount = true;
        return account;
    }

    // ----- domain behaviour -----

    /** Applies a posting to the cached balance, enforcing status and overdraft rules. */
    public void apply(Direction direction, long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        switch (status) {
            case CLOSED -> throw new BusinessRuleException("Account " + id + " is closed");
            case FROZEN -> {
                if (direction == Direction.DEBIT) {
                    throw new BusinessRuleException("Account " + id + " is frozen: debits are not allowed");
                }
            }
            case ACTIVE -> { }
        }
        try {
            if (direction == Direction.DEBIT) {
                if (!allowNegative && balance < amount) {
                    throw new InsufficientFundsException(id, balance, amount);
                }
                balance = Math.subtractExact(balance, amount);
            } else {
                balance = Math.addExact(balance, amount);
            }
        } catch (ArithmeticException ex) {
            throw new BusinessRuleException("Amount would overflow the account balance");
        }
    }

    public void freeze() {
        requireCustomerAccount();
        if (status == AccountStatus.CLOSED) {
            throw new BusinessRuleException("A closed account cannot be frozen");
        }
        status = AccountStatus.FROZEN;
    }

    public void unfreeze() {
        if (status != AccountStatus.FROZEN) {
            throw new BusinessRuleException("Account is not frozen");
        }
        status = AccountStatus.ACTIVE;
    }

    public void close() {
        requireCustomerAccount();
        if (balance != 0) {
            throw new BusinessRuleException("Only accounts with a zero balance can be closed");
        }
        status = AccountStatus.CLOSED;
    }

    private void requireCustomerAccount() {
        if (systemAccount) {
            throw new BusinessRuleException("System accounts cannot be modified");
        }
    }

    // ----- accessors -----

    public UUID getId() { return id; }
    public String getOwnerName() { return ownerName; }
    public String getCurrency() { return currency; }
    public long getBalance() { return balance; }
    public boolean isAllowNegative() { return allowNegative; }
    public boolean isSystemAccount() { return systemAccount; }
    public AccountStatus getStatus() { return status; }
    public Long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
}
