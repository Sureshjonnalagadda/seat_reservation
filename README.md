# Seat Reservation at Scale

Paytm take-home: assigned-seat reservations under concurrency, with observability and a public deploy.

## Submission (reviewer quick path)

| Item | Link / command |
|------|----------------|
| **Repository** | [github.com/Sureshjonnalagadda/seat_reservation](https://github.com/Sureshjonnalagadda/seat_reservation) |
| **Live API** | `https://seat-reservation-api-pyzg.onrender.com` |
| **Engineering write-up** | [WRITEUP.md](WRITEUP.md) |
| **Smoke (health + metrics)** | `.\scripts\smoke-live.ps1 -BaseUrl "https://seat-reservation-api-pyzg.onrender.com"` |
| **Hot-seat burst (live)** | `.\scripts\burst-test.ps1 -BaseUrl "https://seat-reservation-api-pyzg.onrender.com" -Requests 100 -Seat A12` |
| **Tests (clone)** | `.\mvnw.cmd clean test` (Docker required for `ReservationConcurrencyIT`) |
| **Run like production (clone)** | `docker compose up --build` → API on `http://localhost:8080` |

**Logs (deployed):** Render Dashboard → service **seat-reservation-api** → **Logs** (structured JSON with `request_id`, reservation events).  
**Metrics:** `GET /actuator/prometheus` on the live base URL.

**Demo admin** (create shows only): username `admin`, password `adminrole` (seeded by Flyway).

---

## What this service does

- JSON HTTP API: create shows, reserve seats, cancel reservations, read show inventory.
- **Correctness under load:** InnoDB row locks + transactions — no double-sell, per-user limits, idempotent retries.
- **Money:** integer **paise** only (no floats).
- **Identity:** `user_id` comes from the JWT; reserve/cancel bodies must not spoof another user.

**Partial multi-seat requests:** **all-or-nothing**. If any requested seat is unavailable, the whole request declines with **409** and no seats from that request are confirmed.

**Seat lifecycle:** reservations go **directly to `CONFIRMED`** (no time-boxed `HELD` in this submission). Release is **`POST /reservations/{id}/cancel`** (owner or admin). Cancelled seats return to **`AVAILABLE`**.

**JSON status values:** enums are returned in uppercase (`CONFIRMED`, `AVAILABLE`, `CANCELLED`) — same semantics as the assignment’s `confirmed` / `available` examples.

---

## API reference

### Auth

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/auth/register` | — | `{"username","password"}` (password ≥ 8 chars) → `201` |
| `POST` | `/auth/login` | — | Returns `access_token`, `token_type`, `expires_in` |

Use `Authorization: Bearer <access_token>` on protected routes.

### Shows

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/shows` | ADMIN | Create show + seats (`201`) |
| `GET` | `/shows/{id}` | — | Per-seat status + counts (`available`, `held`, `confirmed`, `total_seats`) |

Create body example:

```json
{
  "name": "friday-night",
  "seats": ["A1", "A2", "A3", "A4"],
  "price_paise": 25000,
  "per_user_limit": 4
}
```

Invariant: `available + held + confirmed == total_seats` (enforced by counting every seat row).

### Reservations

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/shows/{id}/reserve` | User | Reserve seats; idempotency key required (header or body) |
| `GET` | `/reservations/{id}` | Owner or ADMIN | Get reservation |
| `POST` | `/reservations/{id}/cancel` | Owner or ADMIN | Cancel; seats become available again |

Reserve body (**no `user_id`**):

```json
{
  "seats": ["A12"],
  "idempotency_key": "550e8400-e29b-41d4-a716-446655440000"
}
```

Idempotency:

- Header `Idempotency-Key` wins over body `idempotency_key`; if both are sent they must match.
- Same key + same seats → **200** + `X-Idempotent-Replay: true`.
- Same key + different seats → **409** `IDEMPOTENCY_KEY_REUSED`.
- Seat taken / limit → **409** (not **5xx**).

Success **201** example fields: `reservation_id`, `show_id`, `user_id`, `seats`, `amount_paise`, `status`.

### Health & metrics

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/health/live` | Process up |
| `GET` | `/health/ready` | **503** if MySQL unreachable |
| `GET` | `/actuator/prometheus` | `reservations_confirmed_total`, `reservations_declined_total{reason=…}`, `seats_available{show_id=…}` |

Optional: `X-Request-ID` on requests; echoed on responses. API JSON bodies include `request_id`.

---

## Run locally

### Option A — Docker (recommended for reviewers)

No JDK/Maven on the host. MySQL is exposed on host port **3307** (container internal **3306**).

```powershell
docker compose up --build
```

- API: `http://localhost:8080`
- JWT secret in compose: `local-dev-only-change-in-production`

### Option B — Maven on host

**Prerequisites:** JDK 17, MySQL 8 with database `seat_reservation` (default `localhost:3306`, user/password `root`/`root`).

```powershell
.\mvnw.cmd clean test
.\mvnw.cmd spring-boot:run
```

Environment template: [.env.example](.env.example). Default JWT if unset: see `application.yml` (`JWT_SECRET`).

Helper scripts: `.\scripts\mvn.ps1`, `.\scripts\run-mvn-tests.ps1` (`-SkipIntegrationTests` skips Testcontainers).

---

## Deploy (Render)

- Blueprint / service: **`render.yaml`**, branch **`main`**, Docker runtime.
- **You must set:** `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` (reachable MySQL from Render).
- **`JWT_SECRET`:** auto-generated when using the blueprint (`generateValue: true`); override in the dashboard if you prefer.
- Health check path: `/health/live`

After deploy:

```powershell
.\scripts\smoke-live.ps1 -BaseUrl "https://seat-reservation-api-pyzg.onrender.com"
```

---

## Concurrency testing

### Integration tests

`ReservationConcurrencyIT` (Testcontainers MySQL): hot seat, idempotency, per-user limit, multi-seat atomicity, cancel. Requires **Docker Desktop**:

```powershell
.\mvnw.cmd clean test
```

### Burst script (assignment “one-command stampede”)

Creates a one-seat show, fires **N** parallel reserves on the same seat (distinct idempotency keys), prints status histogram + **reconciliation** from `GET /shows/{id}`, exits **0** on PASS.

**Local:**

```powershell
.\scripts\burst-test.ps1 -Requests 100 -Seat A12
```

```bash
BASE_URL=http://localhost:8080 REQUESTS=100 ./scripts/burst-test.sh
```

**Live (cold start: run smoke first; burst may take several minutes on free tier):**

```powershell
.\scripts\burst-test.ps1 -BaseUrl "https://seat-reservation-api-pyzg.onrender.com" -Requests 100 -Seat A12
```

**PASS criteria:** exactly one **201**, remaining accounted as **200** or **409**, zero **5xx** / other codes, hot seat **CONFIRMED**, `available + held + confirmed == total_seats`.

---

## Postman

Import **`postman/Seat-Reservation-API.postman_collection.json`** — folders **Local** and **Render (Deployed)**. Details: [postman/README.md](postman/README.md).

---

## Further reading

Design, locking, idempotency, holds vs cancel, partitioning, and AI disclosure: **[WRITEUP.md](WRITEUP.md)**.
