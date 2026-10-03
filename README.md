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

## Status

Phase 1 complete: Flyway schema, health/readiness, Prometheus. Auth and reservation APIs follow on `development`.
