package com.ledger.account;

import java.time.Instant;
import java.util.UUID;

/**
 * The JSON body returned when an account is created.
 *
 * A separate record from the Account entity, for the same reason TransferResponse
 * is separate from LedgerTransaction: returning a JPA entity couples the public
 * API to the database schema, so a column rename becomes a breaking API change.
 *
 * A static factory keeps the mapping in one place rather than repeated at every
 * call site.
 */
public record AccountResponse(
        UUID id,
        UUID ownerId,
        String accountType,
        String currency,
        Instant createdAt
) {
    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getOwnerId(),
                account.getAccountType(),
                account.getCurrency(),
                account.getCreatedAt());
    }
}
