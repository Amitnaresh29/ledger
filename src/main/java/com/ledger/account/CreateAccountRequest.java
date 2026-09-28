package com.ledger.account;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

/**
 * The JSON body of POST /accounts.
 *
 * Note what is NOT here: `id`. The SERVER generates the account id. A client that
 * could choose its own id could collide with an existing account - or worse,
 * deliberately pick one belonging to someone else.
 *
 * Also not here: `createdAt`. The database supplies it via DEFAULT now().
 * A client should never be able to backdate a record in a ledger.
 */
public record CreateAccountRequest(

        @NotNull(message = "ownerId is required")
        UUID ownerId,

        // The database already has a CHECK constraint on this column, so why
        // validate again? Because the CHECK gives you a Postgres error surfacing
        // as a 409 with a constraint name in it, while @Pattern gives a clean 400
        // naming the field and the allowed values.
        //
        // The database is the GUARANTEE; the annotation is the good error message.
        // Same reasoning as @Positive on TransferRequest.amountMinor.
        @NotNull(message = "accountType is required")
        @Pattern(regexp = "ASSET|LIABILITY|EQUITY|REVENUE|EXPENSE",
                 message = "accountType must be one of ASSET, LIABILITY, EQUITY, REVENUE, EXPENSE")
        String accountType,

        @NotNull(message = "currency is required")
        @Pattern(regexp = "^[A-Z]{3}$", message = "currency must be three uppercase letters, e.g. INR")
        String currency
) {}
