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

## Deadlock experiment

200 threads send 1-cent transfers between the same two accounts, half in each direction, for 10 seconds. Each strategy gets a warm-up round and then three timed rounds, and the table shows the medians. The app uses its default pool of 10 database connections, and PostgreSQL its default 1-second `deadlock_timeout`.

```bash
./mvnw test -Dtest=TransferBenchmark   # about 3 minutes, also writes target/benchmark.md
```

| Strategy | Transfers/s | Failed | Deadlocks per 100 transfers | Median latency (ms) | p99 latency (ms) |
|---|--:|--:|--:|--:|--:|
| Sender first | 0.3 | 86% | 625 | 10,042 | 15,067 |
| Sender first, retry on deadlock | 0.4 | 0% | 540 | 12,060 | 12,070 |
| Lower id first | 719 | 0% | 0 | 11 | 5,034 |

- **Sender first:** a transfer from A to B locks A then B, and one from B to A locks B then A. Almost every such pair deadlocks, and PostgreSQL waits a second before cancelling one of them.
- **Retry on deadlock:** no transfer fails, but each one deadlocks about five times before it gets through, so throughput stays under one per second.
- **Lower id first:** both directions lock the accounts in the same order. Transfers queue for the two rows but never deadlock. This is what the app does.
- **Latency:** 200 clients share 10 connections, and the pool serves them unevenly. Half the id-order transfers finished within 11 ms, but 1 in 100 waited 5 seconds.
- **Variance:** across three runs, lower id first managed 719 to 1,137 transfers/s, and sender first 0.3 to 0.7 with 79 to 89% failing. The sender-first strategies complete only a few transfers per round.

Measured with Docker Desktop on Windows 11.

## Roadmap

- [x] 1. Core ledger: accounts, deposits, transfers, entry history, the balance invariant test
- [x] 2. Retries and deadlocks: idempotency keys, the opposing-transfers experiment
- [ ] 3. Client access: API keys with Spring Security, idempotency keys scoped to each client, rate limiting in Redis
- [ ] 4. Deploy: Dockerfile, CI, AWS
- [ ] 5. Batch reconciliation of bank statements with Spring Batch
