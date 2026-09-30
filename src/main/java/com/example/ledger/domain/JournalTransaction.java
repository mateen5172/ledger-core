package com.example.ledger.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;

/**
 * One atomic, balanced group of ledger entries. Once written it is never updated (@Immutable):
 * mistakes are corrected by posting a compensating REVERSAL transaction, never by editing history.
 */
@Entity
@Immutable
@Table(name = "journal_transactions")
public class JournalTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionType type;

    /** Client-supplied key; the unique constraint is the last line of defence against double posting. */
    @Column(nullable = false, unique = true, length = 100, updatable = false)
    private String idempotencyKey;

    /** SHA-256 of the financial content of the request, used to detect key reuse with a different payload. */
    @Column(nullable = false, length = 64, updatable = false)
    private String requestFingerprint;

    @Column(length = 255)
    private String description;

    /** Set on REVERSAL transactions. UNIQUE, so a transaction can be reversed at most once. */
    @Column(unique = true, updatable = false)
    private UUID reversalOf;

    @Column(nullable = false, length = 3)
    private String currency;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "transaction", cascade = CascadeType.PERSIST)
    @OrderBy("id ASC")
    private List<LedgerEntry> entries = new ArrayList<>();

    protected JournalTransaction() {
        // required by JPA
    }

    public JournalTransaction(TransactionType type, String idempotencyKey, String requestFingerprint,
                              String description, UUID reversalOf, String currency) {
        this.type = type;
        this.idempotencyKey = idempotencyKey;
        this.requestFingerprint = requestFingerprint;
        this.description = description;
        this.reversalOf = reversalOf;
        this.currency = currency;
    }

    public void addEntry(LedgerEntry entry) {
        entries.add(entry);
    }

    /** Core double-entry invariant: total debits must equal total credits. */
    public void assertBalanced() {
        long debits = sum(Direction.DEBIT);
        long credits = sum(Direction.CREDIT);
        if (entries.size() < 2 || debits != credits) {
            throw new IllegalStateException("Unbalanced journal transaction: debits=" + debits
                    + ", credits=" + credits + ", entries=" + entries.size());
        }
    }

    private long sum(Direction direction) {
        return entries.stream()
                .filter(e -> e.getDirection() == direction)
                .mapToLong(LedgerEntry::getAmount)
                .sum();
    }

    public UUID getId() { return id; }
    public TransactionType getType() { return type; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getRequestFingerprint() { return requestFingerprint; }
    public String getDescription() { return description; }
    public UUID getReversalOf() { return reversalOf; }
    public String getCurrency() { return currency; }
    public Instant getCreatedAt() { return createdAt; }
    public List<LedgerEntry> getEntries() { return entries; }
}
