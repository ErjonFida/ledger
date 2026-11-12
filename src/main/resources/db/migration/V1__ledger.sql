CREATE TABLE account (
    id       uuid         PRIMARY KEY,
    name     varchar(100) NOT NULL,
    currency varchar(3)   NOT NULL,
    cash     boolean      NOT NULL DEFAULT false,
    balance  bigint       NOT NULL DEFAULT 0,
    -- Only a cash account may go negative: it is where deposits come from
    CHECK (cash OR balance >= 0)
);

CREATE UNIQUE INDEX account_cash_per_currency ON account (currency) WHERE cash;

INSERT INTO account (id, name, currency, cash)
VALUES (gen_random_uuid(), 'Cash EUR', 'EUR', true),
       (gen_random_uuid(), 'Cash USD', 'USD', true),
       (gen_random_uuid(), 'Cash ALL', 'ALL', true);

CREATE TABLE transfer (
    id              uuid        PRIMARY KEY,
    from_account_id uuid        NOT NULL REFERENCES account (id),
    to_account_id   uuid        NOT NULL REFERENCES account (id),
    amount          bigint      NOT NULL CHECK (amount > 0),
    created_at      timestamptz NOT NULL,
    CHECK (from_account_id <> to_account_id)
);

CREATE TABLE entry (
    id          bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    transfer_id uuid        NOT NULL REFERENCES transfer (id),
    account_id  uuid        NOT NULL REFERENCES account (id),
    amount      bigint      NOT NULL CHECK (amount <> 0),
    created_at  timestamptz NOT NULL
);

CREATE INDEX entry_account ON entry (account_id, id);
