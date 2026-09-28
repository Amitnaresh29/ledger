-- Development seed data.
--
-- There is no POST /accounts endpoint yet, so accounts are created directly.
-- These are the exact UUIDs api.http refers to, so every request in that file
-- works afterwards with no editing.
--
--   docker exec -i ledger-postgres psql -U ledger -d ledger < seed.sql
--
-- Safe to re-run: it clears ledger data first. NEVER point this at anything real.

DELETE FROM ledger_entries;
DELETE FROM transactions;
DELETE FROM accounts;

INSERT INTO accounts (id, account_type, owner_id, currency) VALUES
  -- EQUITY: where money enters the system. Allowed to go negative - it
  -- represents value issued into the ledger.
  ('eeeeeeee-0000-0000-0000-000000000001', 'EQUITY', '00000000-0000-0000-0000-000000000000', 'INR'),
  -- Two customer wallets. ASSET accounts may never go negative.
  ('aaaaaaaa-0000-0000-0000-000000000001', 'ASSET',  '11111111-0000-0000-0000-000000000001', 'INR'),
  ('bbbbbbbb-0000-0000-0000-000000000002', 'ASSET',  '22222222-0000-0000-0000-000000000002', 'INR'),
  -- A USD account, to demonstrate that cross-currency transfers are refused.
  ('cccccccc-0000-0000-0000-000000000003', 'ASSET',  '33333333-0000-0000-0000-000000000003', 'USD');

-- Opening funding, so the wallets are usable immediately instead of every
-- session starting with an "Insufficient funds: balance 0" surprise.
--
-- Written as proper double entry: ONE transaction, TWO entries summing to zero.
-- Seed data that broke the invariant would be worse than no seed data at all.
INSERT INTO transactions (id, idempotency_key, description) VALUES
  ('d0000000-0000-0000-0000-000000000001', 'seed-funding-a', 'seed: open account A with Rs 1000'),
  ('d0000000-0000-0000-0000-000000000002', 'seed-funding-b', 'seed: open account B with Rs 1000');

INSERT INTO ledger_entries (id, transaction_id, account_id, amount, currency) VALUES
  -- A: equity out, wallet in
  ('e0000000-0000-0000-0000-000000000001', 'd0000000-0000-0000-0000-000000000001',
   'eeeeeeee-0000-0000-0000-000000000001', -100000, 'INR'),
  ('e0000000-0000-0000-0000-000000000002', 'd0000000-0000-0000-0000-000000000001',
   'aaaaaaaa-0000-0000-0000-000000000001',  100000, 'INR'),
  -- B: same again
  ('e0000000-0000-0000-0000-000000000003', 'd0000000-0000-0000-0000-000000000002',
   'eeeeeeee-0000-0000-0000-000000000001', -100000, 'INR'),
  ('e0000000-0000-0000-0000-000000000004', 'd0000000-0000-0000-0000-000000000002',
   'bbbbbbbb-0000-0000-0000-000000000002',  100000, 'INR');

-- Show the result, and prove the seed itself balances.
SELECT left(a.id::text, 8) AS account, a.account_type, a.currency,
       coalesce(sum(e.amount), 0) AS balance_minor
FROM accounts a LEFT JOIN ledger_entries e ON e.account_id = a.id
GROUP BY a.id, a.account_type, a.currency
ORDER BY a.account_type, a.currency;

SELECT coalesce(sum(amount), 0) AS whole_ledger_must_be_zero FROM ledger_entries;
