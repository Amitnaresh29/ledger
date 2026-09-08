package com.ledger.transfer;

import java.time.Instant;
import java.util.UUID;

/**
 * The JSON body returned by POST /transfers.
 *
 * A SEPARATE record from the entity on purpose. Never return JPA entities from a
 * controller: it couples your public API to your database schema, so a column
 * rename silently becomes a breaking API change, and it can trigger lazy-loading
 * during JSON serialisation.
 */
public record TransferResponse(
        UUID transactionId,
        String idempotencyKey,
        Instant createdAt
) {}
