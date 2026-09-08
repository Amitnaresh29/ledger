package com.ledger.transfer;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

/**
 * The JSON body of POST /transfers.
 *
 * A RECORD, not a class. `record` is Java 16+ shorthand: this one line generates
 * private final fields, a constructor, accessors (fromAccountId(), NOT
 * getFromAccountId()), plus equals, hashCode and toString. Immutable by design,
 * which is exactly what a request payload should be.
 *
 * This is also why we skipped Lombok - records cover the DTO case natively.
 *
 * NOTE: idempotencyKey is NOT here. It travels as an HTTP header, which is the
 * industry convention (Stripe, Adyen and others all use Idempotency-Key). It is
 * metadata about the REQUEST, not part of the money movement being described.
 */
public record TransferRequest(

        // Validation lives on the DTO so bad input is rejected before it ever
        // reaches the service. @NotNull on a UUID catches a missing or null field;
        // a malformed UUID string fails earlier still, during JSON parsing.
        @NotNull(message = "fromAccountId is required")
        UUID fromAccountId,

        @NotNull(message = "toAccountId is required")
        UUID toAccountId,

        // @Positive rejects 0 and negatives. The service checks this too - the DTO
        // check gives a clean 400 with a field name, the service check is the
        // guarantee for any caller that is not the REST layer.
        @Positive(message = "amountMinor must be positive")
        long amountMinor,

        // No annotation: description is genuinely optional.
        String description
) {}
