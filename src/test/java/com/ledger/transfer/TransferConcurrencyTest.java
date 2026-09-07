package com.ledger.transfer;

import com.ledger.account.Account;
import com.ledger.account.AccountRepository;
import com.ledger.entry.LedgerEntryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

// @SpringBootTest, NOT @DataJpaTest - and this matters enormously here.
// @DataJpaTest wraps each test in a transaction and rolls it back, so separate
// threads could never see each other's committed data. Concurrency can only be
// tested with REAL commits, which means no test-managed transaction.
// WebEnvironment.NONE skips starting Tomcat; we only need the beans.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class TransferConcurrencyTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired TransferService transferService;
    @Autowired AccountRepository accounts;
    @Autowired LedgerEntryRepository entries;

    private Account account(String type) {
        return accounts.save(new Account(UUID.randomUUID(), type, UUID.randomUUID(), "INR"));
    }

    @Test
    void an_account_can_never_be_overdrawn_by_simultaneous_transfers() throws Exception {
        Account from = account("ASSET");
        Account to = account("ASSET");

        // Fund `from` with exactly Rs 500 - enough for ONE transfer of Rs 500.
        transferService.transfer("fund", account("EQUITY").getId(), from.getId(), 50_000L, "funding");
        assertThat(entries.balanceOf(from.getId())).isEqualTo(50_000L);

        int attempts = 5;
        ExecutorService pool = Executors.newFixedThreadPool(attempts);

        // A latch is a gate. All threads block on await() until countDown()
        // releases them, so they hit the service at the same instant instead of
        // trickling in one after another. Without this, no race would occur.
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(attempts);
        AtomicInteger succeeded = new AtomicInteger();   // thread-safe counter

        for (int i = 0; i < attempts; i++) {
            final int n = i;
            pool.submit(() -> {
                try {
                    startGate.await();
                    // DIFFERENT idempotency keys: these are five genuinely
                    // different requests racing on the balance, not retries.
                    transferService.transfer("race-" + n, from.getId(), to.getId(), 50_000L, null);
                    succeeded.incrementAndGet();
                } catch (Exception expectedForLosers) {
                    // whoever loses should be rejected - that is correct
                } finally {
                    finished.countDown();
                }
            });
        }

        startGate.countDown();                       // release all five at once
        finished.await(20, TimeUnit.SECONDS);
        pool.shutdown();

        long balance = entries.balanceOf(from.getId());
        System.out.println(">>> succeeded=" + succeeded.get() + "  final balance=" + balance);

        // THE INVARIANT: an account with Rs 500 cannot send Rs 500 more than once.
        assertThat(succeeded.get()).isEqualTo(1);
        assertThat(balance).isGreaterThanOrEqualTo(0L);
    }
}
