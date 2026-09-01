CREATE TABLE accounts(
    id UUID PRIMARY KEY,
    account_type TEXT NOT NULL,
    owner_id UUID NOT NULL,
    currency CHAR(3) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT accounts_account_type_check
        CHECK (account_type IN (
            'ASSET',
            'LIABILITY',
            'EQUITY',
            'REVENUE',
            'EXPENSE'
        )),

    CONSTRAINT accounts_currency_check
        CHECK (currency ~ '^[A-Z]{3}$')
);