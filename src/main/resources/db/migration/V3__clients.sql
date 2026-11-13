CREATE TABLE client (
    id           uuid         PRIMARY KEY,
    name         varchar(100) NOT NULL,
    api_key_hash varchar(64)  NOT NULL UNIQUE
);

ALTER TABLE account ADD COLUMN client_id uuid REFERENCES client (id);
