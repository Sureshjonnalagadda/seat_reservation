# Postman — Seat Reservation API

## Import into Postman (desktop / web) — use these files

Postman **Import** does **not** accept the `collections/seat-reservation-api/` YAML folder tree. That layout is for **Postman CLI** git-native workflows.

**Do this instead:**

1. Postman → **Import** → **Upload Files**
2. Select:
   - `postman/Seat-Reservation-API.postman_collection.json`
3. Optional (same Import flow):
   - `postman/Local.postman_environment.json`
   - `postman/Render.postman_environment.json`

You should see collection **Seat Reservation API** with two folders:

| Folder | Variable | Default target |
|--------|----------|----------------|
| **Local** | `{{local_base_url}}` | `http://localhost:8080` |
| **Render (Deployed)** | `{{render_base_url}}` | `https://seat-reservation-api-pyzg.onrender.com` |

Collection variables are set on the collection; environments duplicate them if you import env files.

## Requests (Phase 1)

- Health — Live → `GET /health/live`
- Health — Ready (DB) → `GET /health/ready`
- Prometheus Metrics → `GET /actuator/prometheus`

## Git-native v3 tree (optional)

`postman/collections/seat-reservation-api/` and `postman/environments/*.environment.yaml` are v3 filesystem format for `postman` CLI (`collection lint`, workspace sync). Keep them in git for agents/CLI; use the `.postman_collection.json` for manual Import.

## Phase 2–5 (collection)

The JSON collection includes auth, admin show create, reserve/get/cancel, health, and Prometheus under **Local** and **Render (Deployed)**.

## Phase 6 — burst concurrency

Hot-seat load is not a Postman request; use `scripts/burst-test.ps1` or `scripts/burst-test.sh` against a running API. See [WRITEUP.md](../WRITEUP.md) and README **Concurrency testing**.
