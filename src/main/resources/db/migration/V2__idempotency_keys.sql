ALTER TABLE transfer ADD COLUMN idempotency_key varchar(100) UNIQUE;
