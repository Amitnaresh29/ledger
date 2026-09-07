package com.ledger.transfer;

import com.ledger.account.Account;
import com.ledger.account.AccountRepository;
import com.ledger.entry.LedgerEntry;
import com.ledger.entry.LedgerEntryRepository;
import com.ledger.transaction.LedgerTransaction;
import com.ledger.transaction.LedgerTransactionRepository;
import org.springframework.stereotype.Service;
// NOTE: Spring's @Transactional, NOT jakarta.transaction.Transactional.
// Spring's supports propagation, isolation, readOnly and rollbackFor.
// We will need isolation when we add the balance check.
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Posts a balanced double-entry transfer between two accounts.
 *
 * Every money movement becomes ONE transaction row plus TWO ledger entries whose
 * amounts sum to zero. The entries are built here and nowhere else - that is the
 * only way the zero-sum invariant can actually be guaranteed.
 */
@Service
public class TransferService {

    // final => assigned once in the constructor and never reassigned.
    // The object is immutable, so it is safe to share across request threads.
    private final AccountRepository accounts;
    private final LedgerTransactionRepository transactions;
    private final LedgerEntryRepository entries;

    // CONSTRUCTOR INJECTION. No @Autowired needed - with a single constructor,
    // Spring uses it automatically and passes in the repository beans.
    // Preferred over @Autowired fields because: fields can be final; the
    // dependencies are explicit rather than hidden; you can build one in a unit
    // test with plain `new`; and a missing dependency fails at STARTUP.
    public TransferService(AccountRepository accounts,
                           LedgerTransactionRepository transactions,
                           LedgerEntryRepository entries) {
        this.accounts = accounts;
        this.transactions = transactions;
        this.entries = entries;
    }

    /**
     * @param amountMinor POSITIVE, in minor units (50000 = Rs 500.00).
     *                    Direction comes from fromAccountId/toAccountId, never
     *                    from the sign of this argument.
     */
    // ONE database transaction wraps this whole method: either the transaction row
    // AND both entries commit, or none of them do. A half-written transfer is
    // impossible. This is the most important line in the class.
    //
    // Rolls back on RuntimeException and Error by default - NOT on checked
    // exceptions. That is why both exceptions below extend RuntimeException.
    @Transactional
    public LedgerTransaction transfer(String idempotencyKey,
                                      UUID fromAccountId,
                                      UUID toAccountId,
                                      long amountMinor,
                                      String description) {

        // ---- STEP 1: has this exact request already been posted? ----
        // A retry must NOT post the money again. It returns the original result,
        // so calling this method twice with the same key has the same effect as
        // calling it once. That is what "idempotent" means.
        Optional<LedgerTransaction> existing = transactions.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {          // Optional is an object, not a boolean:
            return existing.get();           // you ask it a question. No truthiness in Java.
        }

        // ---- STEP 2: validate the request itself ----
        // .equals(), not ==. For objects, == compares REFERENCES (are these the
        // same object in memory?); .equals() compares VALUES. Two UUID objects
        // holding the same id are equal but not ==.
        if (fromAccountId.equals(toAccountId)) {
            throw new TransferRejectedException("Cannot transfer to the same account");
        }
        // Zero is forbidden by the CHECK constraint anyway, and a negative amount
        // would silently reverse the direction of the transfer.
        if (amountMinor <= 0) {
            throw new TransferRejectedException("Amount must be positive, was " + amountMinor);
        }

        // ---- STEP 3: load both accounts, or fail ----
        // findById returns Optional<Account>. orElseThrow takes a lambda that
        // builds the exception - it is only invoked if the Optional is empty,
        // so we do not pay for the string concatenation on the happy path.
        Account from = accounts.findById(fromAccountId)
                .orElseThrow(() -> new AccountNotFoundException(fromAccountId));
        Account to = accounts.findById(toAccountId)
                .orElseThrow(() -> new AccountNotFoundException(toAccountId));

        // ---- STEP 4: the currencies must match ----
        // The database CANNOT check this for us. V4's composite FK guarantees an
        // ENTRY's currency matches ITS account - but an INR -> USD transfer would
        // write a legal INR entry on the INR account and a legal USD entry on the
        // USD account, summing to zero numerically while being nonsense. Real
        // cross-currency movement needs an FX rate and a third account; it is not
        // two entries. So this invariant has to live in Java.
        if (!from.getCurrency().equals(to.getCurrency())) {
            throw new TransferRejectedException(
                    "Currency mismatch: " + from.getCurrency() + " to " + to.getCurrency());
        }
        String currency = from.getCurrency();

        // ---- STEP 5: build the transaction and its two signed entries ----
        LedgerTransaction tx =
                new LedgerTransaction(UUID.randomUUID(), idempotencyKey, description);

        // Money out of `from` is negative, money into `to` is positive.
        // List.of(...) returns an IMMUTABLE list - nobody can add a third entry later.
        List<LedgerEntry> lines = List.of(
                new LedgerEntry(UUID.randomUUID(), tx.getId(), from.getId(), -amountMinor, currency),
                new LedgerEntry(UUID.randomUUID(), tx.getId(), to.getId(),    amountMinor, currency)
        );

        // ---- STEP 6: prove they balance before writing anything ----
        // We just built them, so this "cannot" fail - and that is exactly why it
        // belongs here. It states the invariant in executable form and it will
        // catch the future edit that breaks it. IllegalStateException, not
        // TransferRejectedException: this is a BUG in our code, not bad user input.
        long sum = lines.stream().mapToLong(LedgerEntry::getAmount).sum();
        if (sum != 0) {
            throw new IllegalStateException("Entries do not balance, sum was " + sum);
        }

        // ---- STEP 7: save. Transaction row FIRST ----
        // Order matters: each entry's foreign key points at this transaction, so
        // it must exist first.
        //
        // saveAndFlush, not save: save() only QUEUES the insert until commit.
        // Flushing now means a duplicate idempotency_key - a concurrent request
        // that slipped past step 1 - blows up HERE, inside the method, instead of
        // at commit time outside it.
        transactions.saveAndFlush(tx);
        entries.saveAll(lines);

        return tx;
    }
}
