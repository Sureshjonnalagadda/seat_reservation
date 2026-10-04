# Seat Reservation at Scale — Engineering Write-up

## 1. Problem

The service assigns **specific seats** to users for a show. Many clients can reserve at the same time, including bursts on the same seat (“hot seat”). The system must never sell one seat twice, must enforce **per-user limits**, must support **safe retries** via idempotency keys, and must allow **cancellation** that returns seats to inventory.

## 2. Architecture

- **Spring Boot 3** (Java 17) exposes REST APIs and applies JWT authentication.
- **MySQL 8 (InnoDB)** stores authoritative state: shows, seats, reservations, and per-user booking counters.
- **Flyway** owns schema migrations (including a seeded demo `ADMIN` user).
- **Critical paths use Spring JDBC** with explicit SQL and `@Transactional` boundaries—not JPA—for predictable locking.
- **Row locks** (`SELECT … FOR UPDATE`) serialize conflicting updates on `show_user_booking` and seat rows.
- **Observability**: JSON logs, `X-Request-ID` / `request_id`, Prometheus metrics, liveness/readiness health.

Deployed as a single stateless API (e.g. Render + Aiven MySQL); correctness depends on the database transaction model.

## 3. Atomic reservation decision

When a user reserves seats, the service (inside one transaction):

1. Checks idempotency (existing row for `(show_id, user_id, idempotency_key)`).
2. Locks `show_user_booking` for `(show_id, user_id)` and validates `active_seat_count + requested ≤ per_user_limit`.
3. Locks requested **seat rows in sorted seat_number order** with `FOR UPDATE`.
4. Verifies every seat exists and is `AVAILABLE`.
5. Updates seats to `CONFIRMED`, inserts reservation + `reservation_seats`, increments `active_seat_count`.

`SELECT … FOR UPDATE` blocks other transactions from confirming the same seat until the first transaction commits or rolls back. That is what prevents double-selling: concurrent confirm attempts serialize on the seat row lock; the loser sees non-`AVAILABLE` status and returns **409 SEAT_TAKEN** (or rolls back on limit/idempotency rules)—not an unhandled DB error.

## 4. Hot-seat concurrency

For seat `A12`, many parallel requests each use a **distinct** idempotency key. InnoDB serializes the `FOR UPDATE` on `A12`; exactly one transaction commits `CONFIRMED`. Others fail validation and return **409**. We map business conflicts to 409 intentionally so clients can retry or pick another seat—**not 500**.

## 5. Multi-seat atomicity

A request for `[A1, A2, A3]` is **all-or-nothing**. If `A3` is already `CONFIRMED`, the transaction rolls back after locks/validation; **no** partial update to `A1`/`A2`. Lock order is deterministic (sorted seat numbers) to reduce deadlock risk.

## 6. Per-user limit

Table `show_user_booking` holds `(show_id, user_id) → active_seat_count`. It is locked **before** seats so concurrent requests for the same user cannot both pass the limit check. It is a **serialization point**, not a passive counter updated without locking.

## 7. Idempotency

- **Same key + same canonical request** (hash of show, user, sorted seats): return existing reservation (**200** + `X-Idempotent-Replay: true`).
- **Same key + different body**: **409 IDEMPOTENCY_KEY_REUSED**; no state change.
- **Concurrent duplicate first requests**: unique index on `(show_id, user_id, idempotency_key)`; one insert wins, others resolve via replay or conflict handling—no duplicate reservations.

Header `Idempotency-Key` wins over body `idempotency_key` when both are present; mismatched values → **400**.

## 8. Cancellation

Transactional flow: lock `show_user_booking`, lock reservation, verify owner and `CONFIRMED`, lock seats in order, set seats `AVAILABLE`, delete `reservation_seats`, decrement `active_seat_count`, mark reservation `CANCELLED` with `cancelled_at`.

## 9. Consistency vs availability

Reservation **writes** require MySQL. If the database is unavailable, **readiness** returns **503** and writes fail—**correctness over availability** for booking. We do not claim full availability during a DB partition.

## 10. Observability

- **Request IDs** on every JSON body (first field) and `X-Request-ID` header; echoed in logs via MDC.
- **Structured JSON logs** (Logstash encoder) with events such as `RESERVATION_CONFIRMED`, `SEAT_CONFLICT`, `IDEMPOTENT_REPLAY`.
- **Metrics**: `reservations_confirmed_total`, `reservations_declined_total{reason=…}`, `seats_available{show_id=…}` at `/actuator/prometheus`.
- **Health**: `/health/live` (process up), `/health/ready` (DB ping).

## 11. Database constraints

Uniqueness on `(show_id, seat_number)`, `(show_id, user_id, idempotency_key)`, and `reservation_seats.seat_id` supports defense-in-depth alongside application-level locking.

## 12. Testing

- **Unit / WebMvc tests** for validation, hashing, controllers, and error mapping.
- **Testcontainers MySQL** integration tests (`ReservationConcurrencyIT`) for hot seat, idempotency storm, key reuse, multi-seat atomicity, and cancellation.
- **Burst scripts**: `scripts/burst-test.sh` (bash) and `scripts/burst-test.ps1` (Windows) against a running API.

Run integration tests (requires Docker):

```bash
./mvnw test
```

## 13. Scaling limitations

- Single MySQL primary: write throughput and hot-row contention on popular seats cap QPS for that seat.
- Hikari pool size bounds concurrent DB sessions; increasing pool without DB capacity does not help.
- Single deployable service; horizontal scaling of the API is possible only if **all** instances share one consistent database (still one write primary).
- Hot seats do not use app-level locks; scaling out does not remove InnoDB row serialization on the hot row.

## 14. Future improvements

Read replicas for show browsing, partitioning by `show_id`, short-lived holds/expiry, queue-based admission for extreme hot-seat traffic, Redis for non-authoritative cache, Kafka for async notifications, distributed tracing, autoscaling on CPU/latency. Not implemented in this submission.

## 15. AI usage

Cursor (AI-assisted IDE) was used during this project for: scaffolding Spring Boot structure, drafting JDBC/SQL and REST handlers, suggesting Testcontainers and burst-test patterns, writing README/WRITEUP outlines, and debugging flaky concurrent-test auth (JWT filter and HTTP client behavior). All reservation-path decisions—`SELECT … FOR UPDATE` ordering, idempotency hashing, 409 vs 500 mapping, Flyway schema, and error codes—were checked against the assignment spec and validated with `mvnw test`, `burst-test.ps1`, and manual Postman flows. I reviewed and ran every change locally before commit; AI output was not accepted without reading the diff and matching it to InnoDB transaction semantics.
