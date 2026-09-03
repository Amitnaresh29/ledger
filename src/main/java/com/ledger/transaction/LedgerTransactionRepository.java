package com.ledger.transaction;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerTransactionRepository extends JpaRepository<LedgerTransaction,UUID>{
    Optional<LedgerTransaction> findByIdempotencyKey(String idempotencyKey);
}