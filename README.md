# Payflow

A compact Java 17 / Spring Boot payment **simulation**, backed by PostgreSQL. It demonstrates concurrent idempotent creation, persisted lifecycle outcomes, deterministic provider behavior, and JSON errors. It has no real gateway integration or money movement.

The API is unauthenticated and binds to loopback by default. Use synthetic customer data. This is a local engineering prototype without authorization, settlement, an accounting ledger, webhooks, or implemented refund/cancellation operations.

## Build and run

Requires Java 17, PostgreSQL 15 or 16, and Python 3 for the HTTP demo. The checked-in Gradle 8.14.3 wrapper verifies its distribution checksum.

```sh
./gradlew --no-daemon bootJar
docker compose up -d postgres
java -jar build/libs/payflow.jar
python scripts/demo.py --output build/demo/proof.json
```

On Windows, use `./gradlew.bat`. The development database defaults to `localhost:5432/payflow`, username/password `postgres`/`postgres`. Override `DATABASE_URL`, `DB_USERNAME`, `DB_PASSWORD`, and `PORT` as needed; `BIND_ADDRESS` defaults to `127.0.0.1`.

After building the jar, `docker compose up --build` runs both services. Published ports bind to host loopback; the API container binds internally to all interfaces. Compose retains its original PostgreSQL 15 data volume, waits for database health, and copies the explicitly named `payflow.jar` rather than a wildcard.

The demo uses real HTTP for four concurrent same-key creates, concurrent confirmations, payload/method conflicts, declined receipt replay, unavailable-before-acceptance recovery, and invalid input. It prints JSON proof with three saved terminal receipts. Stop and restart the app against the same database, then verify persistence:

```sh
python scripts/demo.py --verify-receipts build/demo/proof.json
```

## API contract

Create with `POST /api/v1/payments`:

```json
{"amount":12.50,"currency":"USD","customerEmail":"customer@example.com","customerId":"customer-1","description":"Simulated payment"}
```

`amount` uses decimal **major currency units**: `12.50 USD` means twelve dollars and fifty cents. Input supports at most 17 integer and 2 fractional digits, with minimum `0.01`. Currency must be a Java 17 ISO 4217 currency with zero to two minor-unit digits; `JPY` accepts integer-valued amounts, while `BHD` and `XXX` are unsupported. Validation prevents silent rounding. No currency conversion occurs.

Email is required and valid; both its raw and normalized values must be at most 254 characters. Currency is normalized to uppercase and the whole email to lowercase with `Locale.ROOT`; customer lookup is case insensitive. Optional `customerId` and `description` are limited to 100 and 255 characters. Their exact values matter for replay, including null versus an empty string.

Supply `Idempotency-Key` for creation retries. It is global to this unauthenticated simulation, case sensitive, and must contain 1–100 ASCII letters, digits, `.`, `_`, `:`, or `-`. Matching retries return HTTP201 and the same payment's **current committed state**. Numeric amount scale, currency case, and email case do not cause conflicts. A changed amount/currency/email/customerId/description returns HTTP409 `idempotency_conflict`. Keys remain for the lifetime of their row. Omitting the key creates independent payments.

Confirm with `POST /api/v1/payments/{id}/confirm` and `{"paymentMethodId":"pm_success"}`:

| Method | HTTP | Persisted result |
| --- | --- | --- |
| `pm_success` | 200 | `COMPLETED`, UTC `completedAt`, no failure reason |
| `pm_decline` | 200 | `FAILED`, stable failure reason, no `completedAt` |
| `pm_unavailable` | 503 `provider_unavailable` | Remains `PENDING`; no method or outcome consumed |
| Unknown method | 400 `invalid_request` | Remains `PENDING` |

The simulator is pure: no random outcomes, sleeps, network calls, or external effects. `pm_unavailable` always fails **before acceptance**; use `pm_success` to demonstrate recovery. Tests also inject temporary unavailability and retry the same method after recovery. Interrupted operations preserve the interruption flag and fail before acceptance.

Confirmation locks the row. The first committed success/decline decides the outcome. Repeating that method returns the existing HTTP200 receipt without another provider invocation, including after restart. A different method returns HTTP409 `confirmation_conflict`; other existing states return HTTP409 `state_conflict`. `PROCESSING` exists only inside the transaction. Cancellation/refund enum values remain for existing records, without transitions/endpoints.

Read with `GET /api/v1/payments/{id}` or `GET /api/v1/payments/customer/{email}`. `GET /api/v1/payments/health` checks database access and simulation health, rather than a real gateway.

Payment operation errors are JSON with `code`, `status`, `message`, UTC `timestamp`, `path`, `errorId`, and optional field `errors`. Invalid requests use HTTP400, missing payment HTTP404 `payment_not_found`, conflicts HTTP409, known unavailable-before-acceptance HTTP503, and unexpected failures HTTP500 `internal_error` without internal exception details. Missing routes, wrong verbs, and unsupported content types use HTTP404/405/415 JSON errors. A decline is a successful HTTP200 operation returning `FAILED`.

## Verification

Tests require a **dedicated disposable PostgreSQL database**: the test profile uses `create-drop`. Never point tests at development/history data. For example:

```sh
docker run --rm --name payflow-tests -e POSTGRES_DB=payflow_test -e POSTGRES_USER=pay_test -e POSTGRES_PASSWORD=pay_test -p 127.0.0.1:5433:5432 postgres:16-alpine
```

Wait until PostgreSQL reports ready, then in another terminal:

```sh
DATABASE_URL=jdbc:postgresql://127.0.0.1:5433/payflow_test DB_USERNAME=pay_test DB_PASSWORD=pay_test ./gradlew --no-daemon test bootJar
```

PowerShell equivalent:

```powershell
$env:DATABASE_URL = 'jdbc:postgresql://127.0.0.1:5433/payflow_test'
$env:DB_USERNAME = 'pay_test'
$env:DB_PASSWORD = 'pay_test'
./gradlew.bat --no-daemon test bootJar
```

The suite verifies actual PostgreSQL metadata and forces independent request transactions to overlap behind database locks. It checks provider counts, one row per key, payload conflicts, terminal replay, competing confirmation methods, rollback after known/unexpected failures, money/input validation, and reserved lifecycle states. Pure simulator tests cover deterministic identifiers/outcomes and preserved interruption. CI runs PostgreSQL 15 and 16, builds the executable jar, exercises real HTTP, restarts the app to verify saved receipts, and uploads reports/JSON proof.

## Boundaries and existing data

Key serialization uses PostgreSQL transaction-scoped advisory locks before lookup, with explicit `READ_COMMITTED` isolation so a waiting request sees the winner's commit. Confirmation uses a pessimistic row lock. Independent application transactions coordinate through PostgreSQL, without JVM-only mutexes. A key-hash collision only delays unrelated keys; locks release on commit/rollback.

New provider references derive from the assigned payment UUID. Existing `pi_` pending references remain confirmable with simulated methods, and terminal records with a stored matching method remain replayable within current input limits. Rows, columns, and numeric storage are retained; no amounts are rescaled. New validation applies to retries too. New timestamps are UTC at PostgreSQL microsecond precision so immediate responses match saved receipts; old timestamp values are not rewritten.

Schema management remains Hibernate `ddl-auto: update`, appropriate to this unreleased prototype rather than a versioned production migration strategy. Guarantees are bounded to PostgreSQL state and the pure simulator. A real gateway requires provider-side idempotency and reconciliation for ambiguous timeouts or crashes; a database transaction alone cannot guarantee exactly-once external payment effects.
