package com.ledger.account;

import java.util.UUID;

public record BalanceResponse(UUID accountId, String currency, long balanceMinor){
}
