# Ledger

A double-entry ledger and payments service in Java 21 and Spring Boot, backed by Postgres.

This is **not a wallet app**. It is the accounting core that sits underneath one: every money
movement is recorded as balanced debit/credit entries, and the books are guaranteed to balance under
concurrent transfers, retries and partial failures.

---

## Quick start

```bash
docker compose up -d --wait                                           # Postgres 17
docker exec -i ledger-postgres psql -U ledger -d ledger < seed.sql    # demo accounts (optional)
./mvnw spring-boot:run                                                # the service, on :8080
```

```bash
curl localhost:8080/actuator/health          # {"status":"UP"}
```

Flyway applies the schema on startup, so there is nothing else to set up. `seed.sql` is optional —
it just creates four demo accounts with opening balances so you can try the API immediately.

**Run the tests** (needs Docker; Testcontainers starts its own throwaway Postgres):

```bash
./mvnw test        # 21 tests, including a real concurrency test
```

To explore the API by hand, open [`api.http`](api.http) in IntelliJ or in VS Code / Kiro with the
**REST Client** extension, and click *Send Request* on any block.

---

## API

| method | path | description |
|---|---|---|
| `POST` | `/accounts` | Open an account. Server generates the id. |
| `GET` | `/accounts/{id}/balance` | Derived balance, in minor units. |
| `POST` | `/transfers` | Move money. Requires an `Idempotency-Key` header. |

```bash
# open an account
curl -X POST localhost:8080/accounts -H 'Content-Type: application/json' \
  -d '{"ownerId":"7777...","accountType":"ASSET","currency":"INR"}'

# move Rs 500  (50000 minor units)
curl -X POST localhost:8080/transfers -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: transfer-001' \
  -d '{"fromAccountId":"aaaa...","toAccountId":"bbbb...","amountMinor":50000,"description":"A pays B"}'
```

| status | meaning |
|---|---|
| `201` | created |
| `400` | invalid request — a validation failure also names the offending fields |
| `404` | no such account |
| `409` | concurrent conflict; the other request won, re-read before retrying |

Errors share one shape, with a stable machine-readable `error` code clients can branch on:

```json
{ "timestamp": "...", "status": 400, "error": "transfer_rejected",
  "message": "Insufficient funds: balance 50000, requested 99999900" }
```

---

## Design

### Balance is derived, never stored

There is no `balance` column. An account's balance is `SELECT SUM(amount) FROM ledger_entries
WHERE account_id = ?`.

A stored balance is a second source of truth that can silently drift from the journal, and
reconciling the two is exactly the problem double-entry exists to prevent. The trade is read cost for
correctness — and the ledger can be audited at any moment by summing *every* entry, which must
come to zero.

### The journal is append-only

`transactions` has no `status` and no `updated_at`. A transfer either posts completely or leaves no
trace, and a reversal is a **new compensating transaction** — never an `UPDATE` or `DELETE`.

### Money is a signed `BIGINT` in minor units

`50000` means ₹500.00. Never floating point: binary floating point cannot represent `0.10` exactly
and the error compounds across millions of rows. Integer arithmetic is exact by construction, `SUM`
cannot drift, and the sign encodes direction — so the core invariant is a single check that each
transaction's entries sum to zero.

### Idempotency is enforced by the database

`transactions.idempotency_key` carries a `UNIQUE` constraint. The service also checks for an existing
key first and returns the original transaction, but **that check is an optimisation, not the
guarantee** — two simultaneous identical requests both pass it, and the constraint is what lets only
one win. A check-then-insert in application code races; a unique index does not.

### Concurrency: the balance check was not atomic

An overdraft check reads a balance and then writes entries, with a gap between. Under Postgres'
default `READ COMMITTED`, simultaneous transfers all read the *pre-debit* balance and all proceed.

This was reproduced rather than assumed — five concurrent transfers of ₹500 from an account holding
exactly ₹500:

```
succeeded=5   final balance=-200000
```

The account sent ₹2,500 it did not have, with a correct-looking check running every time. Nothing in
the schema could catch it: every `CHECK`, the composite foreign key and the unique key were all
satisfied, because **the invariant that broke spans rows and no per-row constraint can see it.**

The fix is a pessimistic row lock on the sender, taken before the balance is read:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select a from Account a where a.id = :id")
Optional<Account> findByIdForUpdate(@Param("id") UUID id);
```

The lock is on the `accounts` row even though the balance lives in `ledger_entries` — the account row
holds none of the protected data and acts purely as a **mutex**, which works only because every
writer agrees to take it first. `TransferConcurrencyTest` now reports `succeeded=1, final balance=0`.

Only the sender is locked: no decision depends on the receiver's balance, and locking the minimum
avoids lock-ordering deadlocks entirely.

### Invariants live as close to the data as they can be expressed

| invariant | enforced by |
|---|---|
| valid `account_type`, valid currency format, non-zero amounts | `CHECK` constraints |
| an entry's currency matches its account | composite FK `(account_id, currency) → accounts (id, currency)` |
| no duplicate idempotency key | `UNIQUE` |
| a transaction's entries sum to zero | application — it spans rows |
| no cross-currency transfer | application — see below |
| no overdraft under concurrency | pessimistic row lock |

The composite foreign key is worth a note: enforcing "an entry's currency must match its account"
spans two tables, so it cannot be a `CHECK` — but it does not need a trigger either. A redundant-
looking `UNIQUE (id, currency)` on `accounts` makes the pair referenceable, and the foreign key does
the rest declaratively. It also yields a guarantee for free: **an account's currency can never be
changed once it has entries**, which would otherwise corrupt every historical balance.

Cross-currency transfers must be rejected in Java because the database *structurally cannot* see the
problem: an INR→USD transfer would write a legal INR entry on the INR account and a legal USD entry
on the USD account, both satisfying the foreign key and summing to zero numerically. Real
cross-currency movement needs an FX rate and a third account; it is not two entries.

### Flyway owns the schema

`spring.jpa.hibernate.ddl-auto=validate`. Hibernate may only *verify* that entities match the tables;
it never creates or alters them. Schema changes are reviewed SQL migrations in version control, and
drift fails the application at startup rather than at 2am.

### Where money comes from

Money enters the ledger from an **EQUITY** account, which legitimately carries a negative balance —
it represents value issued into the system. Customer **ASSET** accounts may never go negative. This
is why the overdraft check applies only to asset accounts, and why the whole ledger nets to zero.

---

## Testing

21 tests, all against a **real Postgres 17** via Testcontainers — never an in-memory database, which
would have none of the check constraints, the composite foreign key, or Postgres' `bpchar` type, and
would have passed against several genuinely broken versions of the balance query.

| suite | covers |
|---|---|
| repository tests | the balance aggregate, idempotency lookup, and every database constraint |
| service tests | balanced posting, idempotent retry, currency mismatch, unknown account, insufficient funds |
| **concurrency test** | five simultaneous transfers against one funded account |
| API tests | status codes, JSON shape, validation field errors, via MockMvc |

Each run starts a throwaway container, applies all migrations to it, and destroys it afterwards.

---

## Stack

Java 21 · Spring Boot 3.5.16 · Spring Data JPA / Hibernate · Postgres 17 · Flyway · Testcontainers ·
JUnit 5 + AssertJ · Maven (wrapper included)

Postgres is pinned to 17 because Spring Boot 3.5.16 manages Flyway 11.7.2, which supports Postgres up
to 17. The Maven wrapper pins 3.9.16 — use `./mvnw`.

---

## Not built

Deliberately scoped out, and worth naming rather than hiding:

- **Authentication.** Every endpoint is open.
- **`owner_id` has no foreign key** — there is no users table. In a real system identity lives in a
  separate service, and you cannot put a foreign key across a database boundary; here it is simply
  not modelled yet.
- **Multi-currency transfers.** Rejected rather than supported; doing them properly needs FX rates
  and a dedicated account.
- **Reversals, statements, reconciliation reports.** The data model supports them; the API does not
  expose them yet.
- **Pagination** on any future list endpoint.
