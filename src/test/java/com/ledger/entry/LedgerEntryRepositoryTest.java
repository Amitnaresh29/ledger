package com.ledger.entry;

import com.ledger.account.Account;
import com.ledger.account.AccountRepository;
import com.ledger.transaction.LedgerTransaction;
import com.ledger.transaction.LedgerTransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Loads ONLY the JPA slice - entities, repositories, DataSource, Flyway.
// No Tomcat, no controllers. Faster and more focused than @SpringBootTest.
// Each test method runs in a transaction that is ROLLED BACK afterwards,
// so tests cannot leak data into each other.
@DataJpaTest
// @DataJpaTest normally swaps in an in-memory database. We want the real thing,
// so tell it not to replace our DataSource.
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class LedgerEntryRepositoryTest {

    // static => started ONCE for the whole class, not per test method.
    // Same image as production - testing against a different Postgres would
    // undermine the point.
    // @ServiceConnection is the magic: it hands Spring the container's JDBC url,
    // username and password automatically. No properties to configure.
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired AccountRepository accounts;
    @Autowired LedgerTransactionRepository transactions;
    @Autowired LedgerEntryRepository entries;

    @Test
    void balanceOf_sums_every_entry_for_one_account() {
        // two accounts, two transactions, four balanced entries
        Account a = accounts.save(new Account(UUID.randomUUID(), "ASSET", UUID.randomUUID(), "INR"));
        Account b = accounts.save(new Account(UUID.randomUUID(), "ASSET", UUID.randomUUID(), "INR"));

        LedgerTransaction t1 = transactions.save(
                new LedgerTransaction(UUID.randomUUID(), "t-1", "A pays B 500"));
        LedgerTransaction t2 = transactions.save(
                new LedgerTransaction(UUID.randomUUID(), "t-2", "B pays A 200"));

        entries.save(new LedgerEntry(UUID.randomUUID(), t1.getId(), a.getId(), -50000L, "INR"));
        entries.save(new LedgerEntry(UUID.randomUUID(), t1.getId(), b.getId(),  50000L, "INR"));
        entries.save(new LedgerEntry(UUID.randomUUID(), t2.getId(), a.getId(),  20000L, "INR"));
        entries.save(new LedgerEntry(UUID.randomUUID(), t2.getId(), b.getId(), -20000L, "INR"));

        // -50000 + 20000 = -30000. This single assertion would have caught
        // all three defects the earlier version of balanceOf had.
        assertThat(entries.balanceOf(a.getId())).isEqualTo(-30_000L);
        assertThat(entries.balanceOf(b.getId())).isEqualTo(30_000L);
    }

    @Test
    void balanceOf_is_zero_for_an_account_with_no_entries() {
        Account fresh = accounts.save(
                new Account(UUID.randomUUID(), "ASSET", UUID.randomUUID(), "INR"));

        // This is what coalesce(sum(...), 0) exists for: SUM over zero rows
        // returns NULL in SQL, and unboxing NULL into `long` would throw NPE.
        assertThat(entries.balanceOf(fresh.getId())).isZero();
    }

    @Test
    void a_zero_amount_entry_is_rejected() {
        Account a = accounts.saveAndFlush(
                new Account(UUID.randomUUID(), "ASSET", UUID.randomUUID(), "INR"));
        LedgerTransaction t = transactions.saveAndFlush(
                new LedgerTransaction(UUID.randomUUID(), "t-zero", null));

        // 0 is meaningless noise in a journal - ledger_entries_amount_check forbids it.
        LedgerEntry zero = new LedgerEntry(UUID.randomUUID(), t.getId(), a.getId(), 0L, "INR");

        assertThatThrownBy(() -> entries.saveAndFlush(zero))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void an_entry_in_a_different_currency_from_its_account_is_rejected() {
        Account inrAccount = accounts.saveAndFlush(
                new Account(UUID.randomUUID(), "ASSET", UUID.randomUUID(), "INR"));
        LedgerTransaction t = transactions.saveAndFlush(
                new LedgerTransaction(UUID.randomUUID(), "t-ccy", null));

        // "USD" satisfies the currency regex CHECK, so this must be stopped by
        // something else: V4's composite FK on (account_id, currency), which has
        // no matching (thisAccount, USD) pair in accounts.
        LedgerEntry usdOnInr =
                new LedgerEntry(UUID.randomUUID(), t.getId(), inrAccount.getId(), 100L, "USD");

        assertThatThrownBy(() -> entries.saveAndFlush(usdOnInr))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
