package com.example.ledger.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.example.ledger.domain.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    /**
     * Locks the accounts (SELECT ... FOR UPDATE) in a deterministic order. Because every transaction
     * acquires its row locks in the same order, opposing transfers (A→B and B→A) cannot deadlock.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id in :ids order by a.id")
    List<Account> lockAllByIdOrdered(@Param("ids") Collection<UUID> ids);

    Optional<Account> findBySystemAccountTrueAndCurrency(String currency);

    boolean existsBySystemAccountTrueAndCurrency(String currency);

    List<Account> findBySystemAccountTrueOrderByCurrency();

    Page<Account> findBySystemAccountFalse(Pageable pageable);
}
