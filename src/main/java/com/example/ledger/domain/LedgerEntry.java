package com.example.ledger.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;

/** A single immutable line of the ledger: one account, one direction, one positive amount. */
@Entity
@Immutable
@Table(name = "ledger_entries", indexes = @Index(name = "idx_entries_account", columnList = "account_id, id"))
@Check(constraints = "amount > 0")
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id")
    private JournalTransaction transaction;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id")
    private Account account;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 6)
    private Direction direction;

    @Column(nullable = false)
    private long amount;

    /** Account balance right after this entry, so a statement never needs to be recomputed. */
    @Column(nullable = false)
    private long balanceAfter;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    protected LedgerEntry() {
        // required by JPA
    }

    public LedgerEntry(JournalTransaction transaction, Account account, Direction direction,
                       long amount, long balanceAfter) {
        this.transaction = transaction;
        this.account = account;
        this.direction = direction;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
    }

    public Long getId() { return id; }
    public JournalTransaction getTransaction() { return transaction; }
    public Account getAccount() { return account; }
    public Direction getDirection() { return direction; }
    public long getAmount() { return amount; }
    public long getBalanceAfter() { return balanceAfter; }
    public Instant getCreatedAt() { return createdAt; }
}
