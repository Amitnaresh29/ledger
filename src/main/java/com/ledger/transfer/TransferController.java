package com.ledger.transfer;

import com.ledger.transaction.LedgerTransaction;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// @RestController = @Controller + @ResponseBody. The @ResponseBody half is what
// makes every return value serialised straight to JSON instead of being treated
// as the name of an HTML view to render.
@RestController
@RequestMapping("/transfers")     // base path for every method in this class
public class TransferController {

    // Same constructor injection as the service, for the same reasons:
    // final field, explicit dependency, testable with plain `new`.
    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping                          // POST /transfers
    @ResponseStatus(HttpStatus.CREATED)   // 201 instead of the default 200
    public TransferResponse transfer(

            // Pulled from the HTTP header, not the body. Required by default -
            // a missing header produces a 400 before this method body runs.
            @RequestHeader("Idempotency-Key") String idempotencyKey,

            // @Valid is what actually RUNS the annotations on TransferRequest.
            // Without it they are inert decoration and invalid input sails through.
            // @RequestBody parses the JSON into the record.
            @Valid @RequestBody TransferRequest request) {

        // The controller's whole job: translate HTTP into a service call and back.
        // No business logic here - all of it lives in TransferService, which is
        // why the service is testable without any HTTP at all.
        // Note the accessor style: request.fromAccountId(), not getFromAccountId().
        LedgerTransaction tx = transferService.transfer(
                idempotencyKey,
                request.fromAccountId(),
                request.toAccountId(),
                request.amountMinor(),
                request.description());

        return new TransferResponse(tx.getId(), tx.getIdempotencyKey(), tx.getCreatedAt());
    }
}
