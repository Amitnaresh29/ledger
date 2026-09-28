package com.ledger.account;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ledger.entry.LedgerEntryRepository;
import com.ledger.transfer.AccountNotFoundException;
import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
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

    // POST /accounts  - the base path from @RequestMapping, no extra segment.
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)   // 201, and the default 200 would be wrong:
                                          // something was created that did not exist.
    public AccountResponse create(@Valid @RequestBody CreateAccountRequest request) {

        // The server generates the id, exactly as TransferService does. Generating
        // it here rather than letting the database do it means we know the value
        // before the INSERT - which matters for logging and tracing.
        Account account = new Account(
                UUID.randomUUID(),
                request.accountType(),    // record accessors: accountType(), not getAccountType()
                request.ownerId(),
                request.currency());

        // Use what save() RETURNS. Because the id is assigned rather than
        // @GeneratedValue, Spring Data takes the merge() path and returns a
        // DIFFERENT managed instance - the one Hibernate populates createdAt on.
        // Returning `account` here would give the client "createdAt": null.
        Account saved = accounts.save(account);

        return AccountResponse.from(saved);
    }

    @GetMapping("/{id}/balance")
    public BalanceResponse balance(@PathVariable UUID id) {
        Account accounts_data = accounts.findById(id)
                .orElseThrow(() -> new AccountNotFoundException(id));
        long balanceMinor = entries.balanceOf(id);

        return new BalanceResponse(accounts_data.getId(),accounts_data.getCurrency(),balanceMinor);
    }
}
