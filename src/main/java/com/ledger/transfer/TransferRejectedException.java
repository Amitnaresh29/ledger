package com.ledger.transfer;

/**
 * The transfer was well-formed enough to reach the service but breaks a business
 * rule: same account both sides, a non-positive amount, or mismatched currencies.
 * RuntimeException for the same rollback reason as AccountNotFoundException.
 */
public class TransferRejectedException extends RuntimeException {
    public TransferRejectedException(String reason) {
        super(reason);
    }
}
