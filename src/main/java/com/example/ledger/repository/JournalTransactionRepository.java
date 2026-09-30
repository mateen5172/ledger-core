package com.example.ledger.repository;

import java.util.Optional;
import java.util.UUID;

import com.example.ledger.domain.JournalTransaction;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JournalTransactionRepository extends JpaRepository<JournalTransaction, UUID> {

    @EntityGraph(attributePaths = {"entries", "entries.account"})
    Optional<JournalTransaction> findByIdempotencyKey(String idempotencyKey);

    @EntityGraph(attributePaths = {"entries", "entries.account"})
    Optional<JournalTransaction> findWithEntriesById(UUID id);
}
