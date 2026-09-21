package com.ledger.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/**
 * One consistent error shape for every failure the API returns.
 *
 * Consistency matters more than cleverness here: a client should be able to parse
 * ANY error from this service with the same code, whether it is a 400 or a 500.
 *
 * @param error   a stable machine-readable code. Clients branch on THIS, never on
 *                the human message - so the message can be reworded freely without
 *                breaking anyone.
 * @param message a human explanation, for logs and developers.
 * @param fields  per-field problems, present only for validation failures.
 */
// NON_NULL: omit `fields` from the JSON entirely when it is null, rather than
// emitting "fields": null on every single error response.
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        Map<String, String> fields
) {
    // A second, shorter constructor for the common case with no field errors.
    // `this(...)` delegates to the canonical constructor above - that is how a
    // record adds convenience constructors.
    public ApiError(int status, String error, String message) {
        this(Instant.now(), status, error, message, null);
    }
}
