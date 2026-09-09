package com.ledger.account;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ledger.entry.LedgerEntryRepository;
import com.ledger.transfer.AccountNotFoundException;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;


@RestController
@RequestMapping("/accounts")
public class AccountController {
    private final AccountRepository accounts;
    private final LedgerEntryRepository entries;

    public AccountController(LedgerEntryRepository ledgers,AccountRepository accounts){
        this.entries = ledgers;
        this.accounts = accounts;
    }

    @GetMapping("/{id}/balance")
    public BalanceResponse balance(@PathVariable UUID id) {
        Account accounts_data = accounts.findById(id)
                .orElseThrow(() -> new AccountNotFoundException(id));
        long balanceMinor = entries.balanceOf(id);

        return new BalanceResponse(accounts_data.getId(),accounts_data.getCurrency(),balanceMinor);
    }
}
