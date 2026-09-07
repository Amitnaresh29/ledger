package com.ledger.transfer;

import com.ledger.account.Account;
import com.ledger.account.AccountRepository;
import com.ledger.entry.LedgerEntryRepository;
import com.ledger.transaction.LedgerTransaction;
import com.ledger.transaction.LedgerTransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.from;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
// @DataJpaTest loads ONLY the JPA slice, so it ignores @Service beans.
// @Import pulls TransferService into the test context explicitly.
@Import(TransferService.class)
class TransferServiceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired TransferService transferService;
    @Autowired AccountRepository accounts;
    @Autowired LedgerEntryRepository entries;
    @Autowired LedgerTransactionRepository transactions;

    // A small helper so each test does not repeat four lines of setup.
    // Returns a saved INR account owned by a fresh random owner.
    private Account newInrAccount() {
        return accounts.saveAndFlush(
                new Account(UUID.randomUUID(), "ASSET", UUID.randomUUID(), "INR"));
    }
    private Account newUsdAccount() {
        return accounts.saveAndFlush(
                new Account(UUID.randomUUID(), "ASSET", UUID.randomUUID(), "USD"));
    }
    private Account newEquityAccount() {
        return accounts.saveAndFlush(
                new Account(UUID.randomUUID(), "EQUITY", UUID.randomUUID(), "INR"));
    }
    private void fund(Account target, long amountMinor) {
      Account equity = newEquityAccount();
      transferService.transfer("fund-" + target.getId(), equity.getId(), target.getId(), amountMinor, "funding");
    }

    @Test
    void a_transfer_moves_money_and_writes_two_balanced_entries() {
        // ---- ARRANGE: set up the world the test needs ----
        Account from = newInrAccount();
        Account to = newInrAccount();
        fund(from, 100_000L);

        // ---- ACT: do the ONE thing under test ----
        LedgerTransaction tx = transferService.transfer(
                "transfer-1", from.getId(), to.getId(), 50_000L, "A pays B Rs 500");

        // ---- ASSERT: check what should now be true ----
        // funded 100_000, sent 50_000, so the sender is left holding +50_000.
        // The underscores are just readability; the L makes it a long.
        assertThat(entries.balanceOf(from.getId())).isEqualTo(50_000L);
        assertThat(entries.balanceOf(to.getId())).isEqualTo(50_000L);

        // Exactly two entries, not one and not three.
        assertThat(entries.findByTransactionId(tx.getId())).hasSize(2);
    }

    @Test 
    void  a_retry_with_the_same_key_does_not_post_twice(){
        Account account1 = newInrAccount();
        Account account2 = newInrAccount();
        fund(account1, 100_000L);


        LedgerTransaction first = transferService.transfer("transfer-1", account1.getId(), account2.getId(), 50_000L, null);
        LedgerTransaction second = transferService.transfer("transfer-1", account1.getId(), account2.getId(), 50_000L, null);

        assertThat(second.getId()).isEqualTo(first.getId());
        // funded 100_000 and sent 50_000 ONCE, so 50_000 remains.
        // If the retry had posted again this would be 0.
        assertThat(entries.balanceOf(account1.getId())).isEqualTo(50_000L);
    }

    @Test 
    void a_transfer_between_different_currencies_is_rejected(){
        Account usdAccount= newUsdAccount();
        Account inrAccount= newInrAccount();

        assertThatThrownBy(() -> transferService.transfer("transfer-1", usdAccount.getId(), inrAccount.getId(), 50_000L, null))
                .isInstanceOf(TransferRejectedException.class);
    }

    @Test 
    void a_transfer_from_an_unknown_account_is_rejected(){
        Account inrAccount = newInrAccount();
        assertThatThrownBy(() -> transferService.transfer("transfer-1", UUID.randomUUID(), inrAccount.getId(), 50_000L, null))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test 
    void a_transfer_larger_than_the_balance_is_rejected(){
        Account from = newInrAccount();
        Account to = newInrAccount();
        fund(from, 10_000L);          // only Rs 100 available

        assertThatThrownBy(() -> transferService.transfer(
                "t-insufficient", from.getId(), to.getId(), 50_000L, null))   // asking for Rs 500
                .isInstanceOf(TransferRejectedException.class);
    }
}
