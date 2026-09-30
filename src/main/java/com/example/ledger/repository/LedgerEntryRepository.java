package com.example.ledger.repository;

import java.util.List;
import java.util.UUID;

import com.example.ledger.domain.LedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    @EntityGraph(attributePaths = "transaction")
    Page<LedgerEntry> findByAccountId(UUID accountId, Pageable pageable);

    /** Ledger-derived balance of every account that has at least one entry. */
    @Query("""
            select new com.example.ledger.repository.AccountLedgerTotal(e.account.id,
                sum(case when e.direction = com.example.ledger.domain.Direction.CREDIT
                         then e.amount else -e.amount end))
            from LedgerEntry e
            group by e.account.id
            """)
    List<AccountLedgerTotal> totalsPerAccount();

    /** Transactions whose debits and credits do not cancel out (must always be empty). */
    @Query("""
            select e.transaction.id
            from LedgerEntry e
            group by e.transaction.id
            having sum(case when e.direction = com.example.ledger.domain.Direction.DEBIT
                            then e.amount else -e.amount end) <> 0L
            """)
    List<UUID> findUnbalancedTransactionIds();
}
