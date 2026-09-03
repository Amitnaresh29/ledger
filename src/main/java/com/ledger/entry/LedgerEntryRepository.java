package com.ledger.entry;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry,UUID>{
    @Query("SELECT COALESCE(e.amount,0) FROM LedgerEntry e WHERE e.amount=:amount")
    long balanceOf(@Param("accountId") UUID accountId);

    List<LedgerEntry> findByTransactionId(UUID transactionId);
}