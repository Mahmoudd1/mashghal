# Apparel Manufacturing Tracking

Tracks fabric inventory, cutting, the production pipeline across branches, and sales status.

- **Backend** — Spring Boot 4.1 (Java 25), PostgreSQL 18, Flyway, Spring Security
- **Frontend** — Angular 22 (standalone, zoneless, signals), Angular Material, ngx-translate
- **UI language** — Arabic (RTL) by default, English (LTR) via a toggle

## Layout

```
backend/    Spring Boot REST API
frontend/   Angular single-page app
docker-compose.yml   Local PostgreSQL
```

## Prerequisites

| Tool       | Version used here |
| ---------- | ----------------- |
| JDK        | 25                |
| Maven      | 3.9               |
| Node.js    | 26 (>= 26 for Angular 22) |
| PostgreSQL | 18, via Docker    |

## Running locally

**1. Database**

```bash
docker compose up -d
```

Starts PostgreSQL 18 on `localhost:5432` with database/user/password all `apparel`.

**2. Backend** (http://localhost:8080)

```bash
cd backend
./mvnw spring-boot:run
```

Flyway applies every migration in `src/main/resources/db/migration` on start-up.
API docs: http://localhost:8080/swagger-ui.html

**2b. Demo data** (optional, development only)

```bash
APP_SEED_DEMO_DATA=true ./mvnw spring-boot:run
```

Seeds four fabric types with colours and rolls, four models, five cuts (main,
secondary and derby), allocations split across both branches — including one model
fed by two main cuts — and pieces progressed through every stage with some flagged.
It runs only when the `model` table is empty, and goes through the normal services,
so the demo data obeys the same invariants as real data.

Sign in as `admin` / `admin123` (or `entry` / `entry12345` for the data-entry role).

**3. Frontend** (http://localhost:4200)

```bash
cd frontend
npm start
```

`ng serve` proxies `/api` to the backend (see `frontend/proxy.conf.mjs`), so no CORS
configuration is needed in development. If port 8080 is taken, run the backend with
`SERVER_PORT=8081` and the frontend with `API_TARGET=http://localhost:8081 npm start`.

## Deployment

The Angular app is built into the Spring Boot jar, so a deployment is **one
artifact on one port** serving the UI and the API from the same origin. CORS
therefore plays no part in production.

### With Docker (recommended)

```bash
cp .env.example .env      # then fill in the three secrets
docker compose up -d --build
```

The app comes up on `http://localhost:8080` (override with `APP_PORT`). Postgres is
not published to the host — only the app reaches it.

### Without Docker

```bash
cd backend
./mvnw -Pwith-frontend package     # builds the UI and packages it in the jar

SPRING_PROFILES_ACTIVE=prod \
DB_URL=jdbc:postgresql://localhost:5432/apparel \
DB_USERNAME=apparel DB_PASSWORD=... \
APP_JWT_SECRET="$(openssl rand -base64 48)" \
APP_ADMIN_PASSWORD=... \
java -jar target/backend-0.0.1-SNAPSHOT.jar
```

`-Pwith-frontend` downloads a pinned Node and runs `npm ci && npm run build`, so the
build is reproducible and backend-only work stays Node-free.

### Required configuration

The production profile has **no defaults for secrets** and refuses to start without
them, rather than silently running on the development values committed to this
repository.

| Variable | Notes |
| --- | --- |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Database connection |
| `APP_JWT_SECRET` | Session signing key, **>= 32 bytes**. `openssl rand -base64 48` |
| `APP_ADMIN_PASSWORD` | First account's password; rejected if left as `admin123` |
| `APP_ADMIN_USERNAME` | Optional, defaults to `admin` |
| `APP_JWT_TTL` | Optional, defaults to `PT12H` |
| `APP_PORT` | Optional, host port in Compose; defaults to 8080 |

### What the prod profile changes

- API docs and Swagger UI **off**
- Demo seed data **off**
- Error responses carry no message, exception or stack trace
- Actuator exposes `health` only, without details
- Flyway will not baseline an existing database by accident
- Response compression on, graceful shutdown on

### First run

An empty database migrates itself and creates one **OWNER** account from
`APP_ADMIN_USERNAME` / `APP_ADMIN_PASSWORD`. Sign in and change the password. It is
an owner because the person setting the system up is the one who should see purchase
prices; add `ADMIN` and `DATA_ENTRY` users from the Users screen.

### Before going live

- **Terminate TLS in front of the app.** It speaks plain HTTP; put nginx, Caddy or a
  load balancer ahead of it. `forward-headers-strategy: framework` is set so the app
  honours `X-Forwarded-*`.
- **Back up Postgres.** The Compose volume `apparel-pgdata` holds everything; nothing
  in this repository schedules a dump.
- **Health probe** is `GET /actuator/health`, already wired into the image's
  `HEALTHCHECK` and usable as a readiness probe.

## Tests

```bash
cd backend  && ./mvnw test     # integration tests use Testcontainers; skipped without Docker
cd frontend && npm test        # Vitest + jsdom
```

## Configuration

Backend settings live in `backend/src/main/resources/application.yml`. Everything that
differs per environment is overridable via environment variables:

| Variable             | Default                                    |
| -------------------- | ------------------------------------------ |
| `DB_URL`             | `jdbc:postgresql://localhost:5432/apparel` |
| `DB_USERNAME`        | `apparel`                                  |
| `DB_PASSWORD`        | `apparel`                                  |
| `APP_JWT_SECRET`     | development-only placeholder — **must** be set outside local |
| `APP_ADMIN_USERNAME` | `admin`                                    |
| `APP_ADMIN_PASSWORD` | `admin123`                                 |

## Roles

| | Owner | Admin | Data entry |
| --- | --- | --- | --- |
| Read models, cuts, fabric, reports | ✓ | ✓ | ✓ |
| Receiving, stage moves, sales, flagging | ✓ | ✓ | ✓ |
| Fabric / model / cut master data | ✓ | ✓ | |
| Allocations (pieces to models, rolls to cuts) | ✓ | ✓ | |
| Delete anything · manage users | ✓ | ✓ | |
| **Purchase prices, costs, supplier price comparison** | ✓ | | |

Enforced by URL rules in `SecurityConfig`: reads and `POST /api/pipeline/**` need any
authenticated user; every other write and every delete needs `ROLE_ADMIN`. A
`RoleHierarchy` makes `OWNER` imply `ADMIN` imply `DATA_ENTRY`, so rules are written in
terms of the least role that should pass.

**Prices are stripped server-side, not hidden in the UI.** `PricePolicy` is the single
place that decides, and every DTO carrying money has a `withoutPrices()` that the
service applies on the way out; `/api/fabric-prices` is refused outright with 403. A
column the browser never receives cannot be read out of a network tab, and the rule
holds for Swagger and any future client too.

The first account is created on start-up only when the user table is empty
(`APP_ADMIN_USERNAME` / `APP_ADMIN_PASSWORD`, default `admin` / `admin123`) and is an
`OWNER` — the person setting the system up is the one who should see the money. Change
its password before going live.

**Price is optional on a purchase.** Whoever records fabric arriving often does not
know what was paid; the owner fills it in later, and a batch without a price is a
complete record rather than a draft.

## Conventions

- **Schema** is owned by Flyway. Hibernate runs with `ddl-auto: validate` and never
  alters tables.
- **Arabic is the data language.** Domain tables carry `name_ar` (required) and a
  nullable `name_en` for a future bilingual data-entry UI.
- **Fabric stock is tracked per dated intake batch, not per roll.** A purchase is
  aggregate — date, roll count, total weight, price per unit — because that is how
  fabric is actually bought. Per-roll rows would have to invent a weight the purchase
  never stated.
- **The colour breakdown is soft.** Colours are assigned to a batch afterwards and
  need not sum to its total; the shortfall (or excess) is reported as
  `unassignedRolls` / `overAssignedRolls` and never blocks a save.
- **Price is per batch, not per fabric type**, since the same fabric costs different
  amounts on different dates. This is what makes inventory valuation possible.
- **Suppliers hang off the intake batch, not the fabric type.** Which suppliers
  provide cotton is answered by purchase history, so one fabric can come from several
  suppliers and one supplier can provide several fabrics with no extra bookkeeping.
  The supplier is nullable — a purchase recorded before the paperwork catches up
  still saves, and reports group the unattributed batches rather than hiding them.
- **Average price is weighted by quantity** (total spent ÷ total bought), not a mean
  of batch prices: a two-tonne purchase and a fifty-kilo purchase say very different
  things about what a fabric costs, and only the weighted figure reconciles with the
  value of stock on hand.
- **Rolls are materialised at the cutting table, not at intake.** Intake is
  aggregate and states no per-roll weight, so a roll record exists only once someone
  has weighed it to cut it. That keeps intake honest while giving a part-used roll a
  lasting identity a later cut can pick up.
- **Roll count and roll weight move independently.** Weight comes off a batch every
  time fabric is cut; the roll *count* drops exactly once, when a cut finally marks
  the roll done — which may be a later cut than the one that first used it. Every
  mutation reverses its own previous effect before applying the new one, so edits and
  deletes cannot leave a half-applied change behind.
- **Piece counts derive from the marker**, not from typed quantities: total layers ×
  pieces-per-layer-per-size.
- **The branch split derives from which sizes each branch sews.** A model carries a
  sewing branch and every size inherits it; splitting a model means assigning
  individual sizes to branches ("12 and 14 at Agamy, 16 at Smouha"). Branch quantities
  are therefore never typed, so they cannot disagree with the marker — and adding a
  roll, which changes the layer count, flows straight through to the planned figures.
- **Cutting defaults to Agamy**, since that is where it normally happens.
- **A cut creates its model.** Nearly every cut introduces a new model, so the model
  number is entered on the cut form and the model is created from it — name and sewing
  branch included. `cut.primary_model_id` records the link, so the size breakdown
  already knows which model it is for instead of asking again. A cut can still feed
  further models by naming a different number on a size row.
- **Sizes belong to categories** (`اولادي`, `مقاسات محيرة`, `رجالي`, `مقاسات خاصة`) so
  reporting can ask about a whole range without naming every size.
- **Every fabric type has two stock pools**: its regular stock and an optional
  *derby* (at most one, ever, topped up by further intakes). `DERBY` cuts consume the
  derby; `MAIN` and `SECONDARY` cuts consume regular stock. Crossing them is rejected
  in both directions.
- **Reference data** (branches, pipeline stages) ships in migrations because the
  application's rules depend on it. Demo data is separate.
- **Pipeline stages** are rows with a `sequence_no`, seeded with gaps (100/200/300/400)
  so new stages can be inserted without renumbering.
- **Audit**: `audit_event` is an append-only log of stage transitions, roll allocations
  and flag actions, keyed by `entity_type` + `entity_id`.
- **Cut ↔ Model is many-to-many.** One cutting run can feed several models, and a model
  can (rarely) draw from more than one main cut. `cut_model_allocation` is the source of
  truth for planned quantity — there is no flat `planned_quantity` column on `model`.
- **Dates** render as dd/MM/yyyy with Latin digits in both UI languages.
- **Piece counts are never edited directly.** They change only through movements
  (allocation, stage move, receiving, sale), each of which conserves the total — so
  `sum(stage counts) == sum(allocations)` holds per model+branch by construction.
- **Defects are found at receiving inspection.** Flagging is rejected before the
  `RECEIVED` stage, and a flagged piece is non-sellable: `SOLD` is capped at
  `received − flagged`. Flags belong to a model's pipeline, never to a cut — one
  cutting run feeds several models, so a cut-level count could not be attributed.
- **Pipeline stages are ordinal, so they use one hue light→dark**
  (`--stage-*` in `styles.scss`), not four unrelated colours. Every bar is paired
  with labels and a table, so nothing depends on colour alone.

## Scale

Measured against PostgreSQL with **5,000 models and 5,000 cuts** (7,508 allocations,
7,520 stage rows), best of three warm requests, gzipped as production serves them:

| Endpoint | 1,000 each | 5,000 each | Sent (5,000) |
| --- | --- | --- | --- |
| `GET /api/models` | 13 ms | 55 ms | 50 KB |
| `GET /api/cuts` | 12 ms | 14 ms | 1 KB |
| `GET /api/pipeline/models` | 27 ms | 129 ms | 96 KB |
| `GET /api/models/fabric-usage` | 9 ms | 17 ms | 9 KB |
| `GET /api/reports/overview` | 6 ms | 11 ms | 0.5 KB |
| `GET /api/intakes/stock` | 5 ms | 7 ms | 0.7 KB |

A factory producing a few hundred cuts a year reaches 1,000 in several years, so
these are comfortable numbers with room left over.

What the measurements changed:

- **`/api/pipeline/models` was an N+1** — it built each model's view with its own
  queries, so 1,000 models meant thousands of round trips (1.18 s). It now loads
  counts, allocations, branches and stages in five queries and assembles in memory:
  **1.18 s → 27 ms**. Output is byte-identical to the per-model endpoint, which is
  asserted against every model in the demo set.
- **Responses are gzipped in every profile**, not just production. These listings are
  repetitive JSON and compress about 60×; that is what keeps returning a whole
  collection reasonable.
- **The browser was the real limit, not the database.** The pipeline page rendered a
  card per model — six Material buttons per branch, so 1,000 models meant ~12,000
  button instances — and the models page rendered every expansion panel's body even
  while collapsed. Panel bodies are now deferred (`matExpansionPanelContent`) and both
  pages page client-side over the list they already hold (`shared/paging/client-page.ts`),
  which caps the DOM without splitting the request. The pipeline page also gained a
  model search, since paging through hundreds of cards to find one model is no way to
  work.

**Where it would need work beyond this.** Cuts and intakes already page server-side;
models and pipeline hold the whole list in the browser, which is fine into the low tens
of thousands and then wants server-side paging too. `ModelService.fabricUsagePerPiece()`
loads consumption rows and filters in Java rather than SQL — cheap now, worth moving
into the query if fabric history grows large. Nothing here is schema-deep: the indexes
and foreign keys carry the load, and the fixes were all in how results are assembled.

## Build phases

| Phase | Scope | Status |
| ----- | ----- | ------ |
| 1 | Scaffolding: both projects, Docker Compose, baseline schema, i18n/RTL, auth skeleton | done |
| 2 | Fabric & inventory module | done |
| 3 | Models & cuts module | done |
| 4 | Pipeline & branch tracking | done |
| 5 | Reporting | done |
| 6 | Auth & roles | done |
| 7 | Seed data & dashboard | done |
