# Ledger

Double-entry ledger API: accounts, deposits and transfers, where every movement of money is two entries that sum to zero.

**Stack:** Java 25, Spring Boot 4.1, Spring Data JPA + Flyway, PostgreSQL, springdoc-openapi, JUnit, Mockito, Testcontainers.

## Run locally

Needs JDK 25 and Docker.

```bash
docker compose up -d       # PostgreSQL on :5443
./mvnw spring-boot:run     # http://localhost:8094/swagger-ui.html
```

On Windows PowerShell use `.\mvnw.cmd` instead of `./mvnw`.

## Test

```bash
./mvnw verify              # Testcontainers starts a real PostgreSQL in Docker
```

## API

| Method | Path | What |
|---|---|---|
| POST | `/api/accounts` | Open an account: `{"name": "Alice", "currency": "EUR"}`. EUR, USD or ALL |
| GET | `/api/accounts/{id}` | The account and its balance |
| GET | `/api/accounts/{id}/entries` | Its entries, newest first (`page`, `size` up to 100) |
| POST | `/api/accounts/{id}/deposits` | Pay money in: `{"amount": 10000}` |
| POST | `/api/transfers` | `{"fromAccountId": "...", "toAccountId": "...", "amount": 2500}` |

Amounts are whole minor units (cents). Errors come back as problem details (RFC 9457).

The two requests that move money need an `Idempotency-Key` header, any string up to 100 characters. Generate one per transfer, and send the same one when you retry it:

- A retry with the same key and body returns the first response, and moves no more money.
- The same key with a different body returns 422.
- A request that failed (say, for lack of funds) is not stored, so it can be retried with the same key.

## How it works

- Entries are only ever inserted. A transfer writes one negative and one positive entry.
- Each account also stores its balance, updated in the same transaction as the entries.
- A deposit is a transfer from the currency's cash account, the only kind of account that may go negative.
- A database `CHECK` stops every other balance from going below zero.
- A transfer starts by inserting its row with `ON CONFLICT (idempotency_key) DO NOTHING`. If two retries arrive at once, PostgreSQL makes the second wait until the first commits, and it then returns the first one's transfer. A test sends 20 copies at once and checks the money moved once.
- A transfer updates the account with the lower id first, whichever way the money goes. Two opposite transfers then lock the accounts in the same order, so they cannot deadlock.
- A seeded test runs 10,000 random transfers, then checks that all entries sum to zero and every balance equals the sum of its entries.

## Roadmap

- [x] 1. Core ledger: accounts, deposits, transfers, entry history, the balance invariant test
- [ ] 2. Retries and deadlocks: idempotency keys (done), the opposing-transfers experiment
- [ ] 3. Client access: API keys with Spring Security, idempotency keys scoped to each client, rate limiting in Redis
- [ ] 4. Deploy: Dockerfile, CI, AWS
- [ ] 5. Batch reconciliation of bank statements with Spring Batch
