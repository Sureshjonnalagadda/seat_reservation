# Postman — Seat Reservation API

## Import (reviewers)

1. Postman → **Import** → **Upload Files**
2. Select **`postman/Seat-Reservation-API.postman_collection.json`**
3. Optional environments:
   - `postman/Local.postman_environment.json`
   - `postman/Render.postman_environment.json`

## Folders

| Folder | Base URL variable | Default |
|--------|-------------------|---------|
| **Local** | `{{local_base_url}}` | `http://localhost:8080` |
| **Render (Deployed)** | `{{render_base_url}}` | `https://seat-reservation-api-pyzg.onrender.com` |

## Included requests

- Auth: register, login (user + admin)
- Shows: create (admin), get by id
- Reservations: reserve (with idempotency), get, cancel
- Health: live, ready
- Metrics: Prometheus

**Typical flow:** Admin Login → Create Show → User Login → Reserve → Get Show (verify seat status).

## Burst / load testing

Not in Postman — use the repo scripts (see root [README.md](../README.md)):

```powershell
.\scripts\burst-test.ps1 -BaseUrl "https://seat-reservation-api-pyzg.onrender.com"
```

## Optional: v3 YAML tree

`postman/collections/seat-reservation-api/` is for **Postman CLI** only. Manual import uses the **`.postman_collection.json`** file above.
