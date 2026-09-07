package com.ledger.transfer;

import java.util.UUID;

/**
 * Extends RuntimeException, not Exception, deliberately: @Transactional rolls back
 * on RuntimeException by default but NOT on checked exceptions. A checked exception
 * here would leave a half-written transfer committed.
 */
public class AccountNotFoundException extends RuntimeException {
    public AccountNotFoundException(UUID accountId) {
        super("No account with id " + accountId);
    }
}
