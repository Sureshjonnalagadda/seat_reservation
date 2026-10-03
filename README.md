# Seat Reservation at Scale

Backend API for the Paytm take-home exercise: assigned-seat reservations under concurrency, with observability and a public deploy.

## Quick start (local)

**Prerequisites:** JDK 17, local MySQL 8 (`seat_reservation` database), Maven.

```powershell
mvn clean test
mvn spring-boot:run
```

- Liveness: `GET http://localhost:8080/health/live`
- Readiness (DB): `GET http://localhost:8080/health/ready`
- Metrics: `GET http://localhost:8080/actuator/prometheus`

Environment variables: see [.env.example](.env.example).

Docker (uses MySQL on host port **3307** to avoid clashing with local MySQL):

```powershell
docker compose up --build
```

## Deployment (Render)

- Connect GitHub repo; deploy **Docker** from branch **`main`** (`render.yaml`).
- Set `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET` in Render.
- Health check path: `/health/live`

After deploy:

```powershell
.\scripts\smoke-live.ps1 -BaseUrl "https://YOUR-SERVICE.onrender.com"
```

## Postman

Import **`postman/Seat-Reservation-API.postman_collection.json`** (not the `collections/` YAML folder). See [postman/README.md](postman/README.md).

Live base URL: `https://seat-reservation-api-pyzg.onrender.com`

## Auth (Phase 2)

- `POST /auth/register` — `{"username","password"}` (password min 8 chars) → `201` with `user_id`, `username`, `role`
- `POST /auth/login` — returns `access_token`, `token_type`, `expires_in`
- Protected routes require `Authorization: Bearer <token>` (JWT includes `sub`, `role`, `uid`)

Set `JWT_SECRET` (min 32 characters) in production.

**Demo admin** (seeded by Flyway `V2__seed_admin_user.sql`): username `admin`, password `adminrole`. Use for `POST /shows` only — not for production.

## Shows (Phase 3)

- `POST /shows` — ADMIN only; creates show + seats in one transaction (`201`)
- `GET /shows/{id}` — public; per-seat status and derived counts

Example create body:

```json
{
  "name": "Mumbai Concert",
  "seats": ["A1", "A2", "A3", "A4"],
  "price_paise": 150000,
  "per_user_limit": 4
}
```

## Reservations (Phase 4)

- `POST /shows/{id}/reserve` — authenticated; `Idempotency-Key` header or body `idempotency_key` (header wins; both must match if present)
- `GET /reservations/{id}` — owner or ADMIN
- `POST /reservations/{id}/cancel` — owner or ADMIN

Reserve body (no `user_id`):

```json
{
  "seats": ["A1", "A2"],
  "idempotency_key": "550e8400-e29b-41d4-a716-446655440000"
}
```

## Status

Phase 4: reserve, get, and cancel with JDBC locking and idempotency. Next: observability and burst testing on `development`.
