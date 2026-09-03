package com.ledger.entry;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry,UUID>{
    @Query("select coalesce(sum(e.amount), 0) from LedgerEntry e where e.accountId = :accountId")
    long balanceOf(@Param("accountId") UUID accountId);

    List<LedgerEntry> findByTransactionId(UUID transactionId);
}