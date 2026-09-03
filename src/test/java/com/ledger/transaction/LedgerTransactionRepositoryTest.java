package com.ledger.transaction;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class LedgerTransactionRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired LedgerTransactionRepository transactions;

    @Test
    void findByIdempotencyKey_finds_a_saved_transaction_and_is_empty_for_an_unknown_key() {
        UUID id = UUID.randomUUID();
        transactions.save(new LedgerTransaction(id, "t-1", "A pays B 500"));

        Optional<LedgerTransaction> found = transactions.findByIdempotencyKey("t-1");

        // isPresent() then get() - never call get() without asserting presence first,
        // or a failure shows up as NoSuchElementException instead of a clear message.
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(id);

        // The absent case matters just as much: this is the branch the transfer
        // service takes for a FIRST request, so it must return empty, not throw.
        assertThat(transactions.findByIdempotencyKey("never-used")).isEmpty();
    }

    @Test
    void a_reused_idempotency_key_is_rejected() {
        transactions.saveAndFlush(new LedgerTransaction(UUID.randomUUID(), "t-1", "original"));

        // Same key, different id - what an accidental retry looks like at the DB level.
        LedgerTransaction retry = new LedgerTransaction(UUID.randomUUID(), "t-1", "accidental retry");

        // saveAndFlush, NOT save: save() only queues the INSERT, and @DataJpaTest
        // rolls back, so the statement might never reach Postgres. Flushing sends
        // it now, which is where the unique constraint fires.
        assertThatThrownBy(() -> transactions.saveAndFlush(retry))
                .isInstanceOf(DataIntegrityViolationException.class);
        // Note the exception type: Spring translated Postgres' SQLException into its
        // own DataIntegrityViolationException. You never catch a JDBC exception.
    }
}
