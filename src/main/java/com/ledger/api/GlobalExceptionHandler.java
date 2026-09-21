package com.ledger.api;

import com.ledger.transfer.AccountNotFoundException;
import com.ledger.transfer.TransferRejectedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Translates exceptions into HTTP status codes, for every controller at once.
 *
 * @RestControllerAdvice = @ControllerAdvice + @ResponseBody. Spring registers it
 * globally: any exception escaping ANY controller is offered to the handlers here,
 * and the most specific matching one wins. Without it, every uncaught
 * RuntimeException becomes a bare 500.
 *
 * This class is where the exception TYPES chosen back in the service finally earn
 * their keep. That is the reason TransferRejectedException and
 * AccountNotFoundException are separate classes rather than one exception with a
 * message: distinct types map to distinct HTTP meanings.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // 404 - the caller asked for something that does not exist.
    @ExceptionHandler(AccountNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiError handleAccountNotFound(AccountNotFoundException ex) {
        return new ApiError(404, "account_not_found", ex.getMessage());
    }

    // 400 - the request was understood but breaks a business rule:
    // same account both sides, non-positive amount, currency mismatch,
    // insufficient funds. The caller can fix it and retry.
    @ExceptionHandler(TransferRejectedException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleTransferRejected(TransferRejectedException ex) {
        return new ApiError(400, "transfer_rejected", ex.getMessage());
    }

    // 409 CONFLICT - and this is the interesting one.
    //
    // It is the lost idempotency race: two identical requests arrived at once,
    // both passed the service's step-1 check, and the UNIQUE constraint let only
    // one win. The loser's transaction rolled back, so NO money moved twice.
    //
    // This could not be handled inside the service, because in Postgres a
    // constraint violation aborts the whole transaction - the catch block could
    // not have re-read the winner's row. Here we are OUTSIDE that transaction,
    // which is exactly why this belongs at the HTTP layer.
    //
    // 409 tells the client: your request conflicted with a concurrent one; the
    // other one succeeded, so re-read rather than retrying blindly.
    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiError handleConflict(DataIntegrityViolationException ex) {
        // Log the real cause but do NOT return it: the message contains table,
        // column and constraint names. Leaking your schema in an error body
        // hands an attacker a free map of your database.
        log.warn("Conflict, likely a duplicate idempotency key", ex);
        return new ApiError(409, "conflict",
                "The request conflicted with a concurrent one. Re-read before retrying.");
    }

    // 400 - @Valid on TransferRequest failed. This exception carries WHICH fields
    // were wrong, so tell the client instead of making them guess.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleValidation(MethodArgumentNotValidException ex) {
        // A stream: take every field error, and collect it into a Map of
        // fieldName -> message. The third argument to toMap is a MERGE function,
        // needed because one field can fail two annotations at once - without it
        // a duplicate key throws IllegalStateException.
        Map<String, String> fields = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fe -> fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage(),
                        (first, second) -> first));

        return new ApiError(java.time.Instant.now(), 400, "validation_failed",
                "One or more fields are invalid", fields);
    }

    // NOTE: there is deliberately NO @ExceptionHandler(Exception.class) here.
    //
    // A blanket catch-all looks tidy and would silently REGRESS working behaviour:
    // Spring already turns a missing required header and malformed JSON into clean
    // 400s, and a catch-all would swallow those and return 500 instead.
    //
    // To add one safely, this class should extend ResponseEntityExceptionHandler,
    // which keeps Spring's own exception handling intact. Until then, unhandled
    // exceptions fall through to Spring's default 500 - which is the correct
    // status for "our bug" anyway.
}
