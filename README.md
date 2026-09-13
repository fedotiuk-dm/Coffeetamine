# Coffeetamine — Backend

Location-based social discovery for **vibe-based coffee micro-encounters**. Not dating, not
messaging. The product loop:

> Google login → onboarding wizard → set status (Ready / Not Ready + optional mood) → appear on
> map → tap nearby user pin → view their mood board (6 Unsplash images) + interests → send
> **ping** → mutual ping = **match**. No chat in MVP.

Full product spec: [`PRODUCT_CONCEPT.md`](PRODUCT_CONCEPT.md). The doc is the source of truth
for domain rules — if the code disagrees, the doc wins.

---

## Status

- **Version**: `0.1.0-SNAPSHOT` (MVP feature-complete; tests deferred per
  [`architecture/MVP_SCOPE_CUTS.md`](architecture/MVP_SCOPE_CUTS.md))
- **Java**: 25 (preview features disabled)
- **Spring Boot**: 4.1 (Spring Modulith 2.1)
- **Identity**: provider-agnostic OIDC (mobile uses OIDC Authorization Code + PKCE; local dev uses `mock-oauth2-server`, production uses ZITADEL / Keycloak)

---

## Quick start

```bash
make up            # Postgres + mock-oauth2-server + backend (Liquibase auto-applies)
make token         # fetch a dev JWT (USER role)
make token-admin   # USER + ADMIN (email matches APP_ADMIN_EMAILS allowlist)

# Service URLs
#   Backend          http://localhost:8090
#   Module graph     http://localhost:8090/actuator/modulith
#   Per-module load  http://localhost:8090/actuator/prometheus
#   Swagger UI       http://localhost:8090/swagger-ui.html
#   OIDC provider    http://localhost:8081/default   (mock-oauth2-server)
#   App Postgres     localhost:5432                  (coffeetamine / coffeetamine)
#   JDWP debug       localhost:5005                  (attach from IntelliJ)
```

All daily tasks have `make` targets — see `make help`. Building/running locally without Docker
needs Maven on host (no wrapper shipped) and matching env vars listed in `application.yml`.

> ⚠️ **Do not** run `mvn` build/start/deploy commands without explicit user approval — see
> parent `CLAUDE.md`.

---

## Module map

Multi-module Maven. Each feature module is a bounded context. `common` is shared primitives,
`main` is the Spring Boot bootstrap that wires everything.

```
forgejo/
├── pom.xml                   # parent — Spring Boot 4.1, Java 25, shared plugins
├── common/                   # security helpers, RFC 7807 errors, base entities, Spring Modulith events
├── user/                     # profile + onboarding wizard (atomic write across modules)
├── interests/                # fixed global tag catalog + per-user selections
├── presence/                 # Ready/NotReady + jittered location + mood comment
├── mood-board/               # 6 Unsplash image refs per user (URL + photoId + attribution)
├── discovery/                # nearby-user filter + compatibility scoring
├── ping/                     # ping/pong + idempotent match detection
├── notification/             # in-app inbox + push dispatcher + device registry
├── main/                     # @SpringBootApplication, SecurityConfig, application.yml
├── architecture/             # design docs (mobile auth, Liquibase conventions, scope cuts)
├── openapi/                  # OpenAPI 3 specs (one per module, generated → API stubs)
└── docker/                   # dev infra config (mock-oauth2-server, Postgres init)
```

| Module                          | Bounded context                             | Key invariant                                         |
|---------------------------------|---------------------------------------------|-------------------------------------------------------|
| [`common`](#common)             | Shared primitives — no domain               | RFC 7807 errors with stable `errorCode`s              |
| [`user`](#user)                 | Identity (`app_user_id` UUID) + onboarding state | Onboarding is **atomic** across modules               |
| [`interests`](#interests)       | Global tag catalog                          | Free-form tags forbidden — fixed catalog only         |
| [`presence`](#presence)         | Status + jittered location                  | Raw GPS **never** persisted, only jittered            |
| [`mood-board`](#mood-board)     | 6 Unsplash refs per user                    | Exactly 6 images; binaries never proxied              |
| [`discovery`](#discovery)       | Nearby-user filter                          | Conjunctive: `READY ∧ distance ∧ compatibility`       |
| [`ping`](#ping)                 | Ping/pong + match                           | Mutual ping → exactly one match (DB-level idempotent) |
| [`notification`](#notification) | Inbox + push                                | Listener idempotent via Spring Data `existsBy`            |

Cross-module communication never imports another module's package, and goes two ways: **reads**
through synchronous `common.spi` ports (`UserProfileLookup`, `PresenceLookup`, … — impl in the owner
module, injected by Spring), **reactions** through async events in `common.event` (ping →
notification). `ApplicationModules.verify()` (`ModularityTests`) fails the build if any module
references another.

---

## Modules in detail

> Reading guide. Each module section follows the same shape: **What it does** (one sentence in
> product terms) → **Why it exists** (the problem it solves) → **How it works** (the main flow)
> → **Endpoints / Integration ports / Tables**. Skim the first three bullets to understand the
> module; consult the tables when you need to wire code against it.

### common

**What it does.** Shared infrastructure — not a feature module. Defines security helpers, the
RFC 7807 error contract, base JPA superclasses, and the cross-module event vocabulary (`common.event`) that the other
modules build on.

**Why it exists.** Every feature module needs "who is calling?", "how do I throw a 404 that
the mobile client can dispatch on?", "how do I publish an event without leaking my own
internals?". `common` answers those questions in one place so the answer doesn't fork across
modules.

**What's inside.**
- **Security**: `AuthenticatedUser` (JWT principal record), `SecurityUtils.getCurrentUserId()`
  to read the resolved internal `app_user_id`, `@RequiresAuthenticated` / `@RequiresAdmin`
  aspect annotations for controllers, `UserOnboardingGuard` SPI interface (the impl lives in the
  `user` module, injected by Spring — only the contract is here).
- **Errors (RFC 7807)**: `ErrorCode` enum holding every machine-readable code in the system
  (`AUTH_*`, `USER_*`, `PING_*`, …). Throw `NotFoundException`, `BadRequestException`,
  `ConflictException`, `ForbiddenException`, `UnauthorizedException` — `GlobalExceptionHandler`
  translates them (plus framework exceptions like validation, type-mismatch, JSON parse) into
  `application/problem+json` responses. The filter chain (401/403) goes through the same
  factory via `ProblemDetailSecurityHandler`.
- **Domain primitives**: `UnorderedPair.of(a, b)` — sorts UUIDs so `(A,B)` and `(B,A)` always
  hash the same way, which is what makes ping/match dedupe work at the DB level. Plus
  `BaseEntity`, `BaseAuditableEntity`, `AppUserKeyedEntity` JPA superclasses for consistent
  audit columns.
- **SPI + events**: `common.spi` holds the cross-module **read/write ports** (`UserProfileLookup`,
  `PresenceLookup`, `UserInterestsLookup`/`Writer`, `MoodBoardLookup`/`Writer`, `UserOnboardingGuard`)
  — called synchronously, impls live in the owner modules. `common.event` holds the **reaction
  events** (`DomainEvent`, `PingSentEvent`, `PingMatchedEvent`) delivered async to
  `@ApplicationModuleListener`s via Spring Modulith's durable JDBC registry. Event consumers are
  idempotent via Spring Data `existsBy`.

**Tables**: `event_publication`, `event_publication_archive` (Spring Modulith registry; app infra).

---

### user

**What it does.** Stores the user's profile (display name, avatar, bio, onboarding flag) and
owns the **onboarding wizard endpoint** that saves the profile and fans interests + mood-board
out to their modules via an event.

**Why it exists.** Identity lives in the OIDC provider, but the app needs domain-specific
facts about the user (their nickname, bio, whether they finished the wizard) that don't
belong in an identity provider. The module is also the orchestration point for the onboarding
wizard — without it, the mobile app would have to call three endpoints in sequence and handle
partial failures itself.

**How it works.**

1. First time a freshly-logged-in user hits any endpoint, `GET /api/users/me` returns
   `404 USER_002` ("complete the wizard first"). Mobile client interprets that as "show the
   onboarding screen".
2. Mobile submits the full wizard payload to `POST /api/users/me/onboarding`:
   `{ name, avatarUrl?, about?, interestIds[1..20], moodBoardImages[6] }`.
3. Server opens **one transaction**: upserts the `user_profiles` row (`onboardingCompleted=true`),
   then calls the `common.spi` `UserInterestsWriter` + `MoodBoardWriter` ports (impls in interests /
   mood-board) to replace the user's selections + 6 images.
4. Any sub-write that throws (unknown tag, non-Unsplash URL, validation) rolls back the **whole**
   request — never partial state. Synchronous + atomic.
5. Re-submitting the wizard later overwrites every section — used by mobile to "resume" a flow.

After onboarding, `PUT /api/users/me` lets the user patch name/avatar/about. The wizard
endpoint stays available for full re-runs.

**Endpoints**

| Method | Path                       | OperationId            |
|--------|----------------------------|------------------------|
| `GET`  | `/api/users/me`            | `getMyUserProfile`     |
| `PUT`  | `/api/users/me`            | `updateMyUserProfile`  |
| `POST` | `/api/users/me/onboarding` | `completeOnboarding`   |
| `GET`  | `/api/users/{userId}`      | `getPublicUserProfile` |

**SPI exposed**: `UserProfileLookup` (read profile summaries by id, batchable — used by discovery,
ping, notification) + the `common` `UserOnboardingGuard` SPI impl.

**Entities**: `UserProfile`. **Tables**: `user_profiles` (PK `user_id`, index on
`onboarding_completed`).

**Endpoints**

| Method | Path                       | OperationId            |
|--------|----------------------------|------------------------|
| `GET`  | `/api/users/me`            | `getMyUserProfile`     |
| `PUT`  | `/api/users/me`            | `updateMyUserProfile`  |
| `POST` | `/api/users/me/onboarding` | `completeOnboarding`   |
| `GET`  | `/api/users/{userId}`      | `getPublicUserProfile` |

**Onboarding atomicity** (the load-bearing invariant). `POST /api/users/me/onboarding` writes profile
+ interests + mood-board inside a single `@Transactional`, calling the `common.spi`
`UserInterestsWriter` / `MoodBoardWriter` ports. Any sub-write failure rolls back the whole request —
never partial state. Idempotent: re-submitting overwrites every section. See
`UserProfileServiceImpl.completeOnboarding`.

**SPI exposed**
- `UserProfileLookup` (read profile summaries by id, batchable — used by discovery, ping, notification).
- `UserOnboardingGuard` impl (interface in `common`).

**Entities**: `UserProfile`. **Tables**: `user_profiles` (PK `user_id`, index on `onboarding_completed`).

---

### interests

**What it does.** Maintains a global catalog of interest tags (admin-curated) and lets each
user pick an **ordered subset** of them. The selection is what discovery uses to compute
compatibility between users.

**Why it exists.** PRODUCT_CONCEPT §6 forbids free-form tags in MVP — if users could type
"coffee lovers!!" vs "Coffee" vs "☕", the compatibility scorer would be meaningless. A fixed
catalog gives the discovery filter a stable signal to overlap on. The catalog is admin-managed
(seeded via Liquibase, edited via the `/admin/` endpoints) so product can curate the taxonomy.

**How it works.**

- **Catalog side** (`/api/interests`, `/api/admin/interests`). Read endpoints are paginated
  and filterable (`search`, `category`, `activeOnly`). Admin endpoints `POST` / `PUT` /
  `DELETE` (soft delete via `active=false`) require the `ADMIN` realm role. Tags have a
  `sortOrder` to control display order; new tags get the next free slot.
- **User side** (`/api/users/me/interests`). `PUT` replaces all selections atomically with the
  supplied ordered list. Server validates: no duplicates within the request, every ID exists
  and is `active`, and the count is ≤ 20. The replace runs as
  `deleteByUserId → flush → saveAll(newSelections)` so the unique `(user_id, selection_order)`
  constraint doesn't trip mid-write. Selection order is preserved on subsequent reads.
- **Onboarding integration.** The `common.spi` `UserInterestsWriter` impl delegates to the same
  `InterestService.replaceUserInterests(userId, ids)`, so onboarding (inside the user transaction) and
  the `PUT` endpoint share identical validation.

**Endpoints**

| Method   | Path                            | OperationId                                                     |
|----------|---------------------------------|-----------------------------------------------------------------|
| `GET`    | `/api/interests`                | `listInterests` (paginated, `search`, `category`, `activeOnly`) |
| `GET`    | `/api/interests/{id}`           | `getInterest`                                                   |
| `POST`   | `/api/admin/interests`          | `createInterest` (ADMIN)                                        |
| `PUT`    | `/api/admin/interests/{id}`     | `updateInterest` (ADMIN)                                        |
| `DELETE` | `/api/admin/interests/{id}`     | `deactivateInterest` (ADMIN — soft delete)                      |
| `GET`    | `/api/users/me/interests`       | `getMyInterests`                                                |
| `PUT`    | `/api/users/me/interests`       | `replaceMyInterests` (atomic replace; preserves order)          |
| `GET`    | `/api/users/{userId}/interests` | `getUserInterests`                                              |

**Cross-module SPI**: exposes `UserInterestsLookup` (interest-id sets for compatibility scoring +
ordered summaries for the detail view — used by discovery) and `UserInterestsWriter` (onboarding).

**Entities**: `InterestTag`, `UserInterestSelection`. **Tables**:
- `interest_tags` (UK `code`, catalog index on `(active, category, sort_order, display_name)`)
- `user_interest_selections` (UK `(user_id, interest_id)`, UK `(user_id, selection_order)`,
  FK `interest_id → interest_tags.id`)

---

### presence

**What it does.** Tracks whether each user is currently **Ready** to meet someone (and where
they are, approximately) or **Not Ready** (hidden from the map). Holds an optional mood/vibe
comment users attach to their Ready state ("at Aroma Kava, working on laptop").

**Why it exists.** This is the on/off switch for the whole product loop. A user is only ever
visible on the map when their presence is `READY` *and* has a location set. Going `NOT_READY`
(or just closing the app) takes them off the map immediately. PRODUCT_CONCEPT §10 + §12
mandate that location must be imprecise — this module enforces that at the persistence layer.

**How it works.**

1. **Going Ready.** Mobile gets a raw GPS fix (CoreLocation / FusedLocationProvider),
   `PUT /api/presence/me` with `{ status: READY, mood?, rawLatitude, rawLongitude }`.
2. **Server jitters the coordinates.** `PresenceServiceImpl.jitter()` picks a uniform random
   distance in `[0, coffeetamine.presence.jitter-max-meters]` (default 80m) and a uniform
   random bearing, then offsets the input by that vector. **Only the jittered coords are
   persisted.** Raw GPS never reaches the database, never reaches any response. Two users
   sitting at the same table get two pins ~80m apart.
3. **Going Not Ready.** `DELETE /api/presence/me` flips status to `NOT_READY` and clears
   location + mood. The row stays (PK is `user_id`) — it's an upsert model.
4. **Read side.** `GET /api/presence/me` always returns jittered coords (same numbers
   forever — re-running `PUT` re-jitters, so the offset isn't stable across re-Ready calls;
   that's intentional, prevents triangulation by an attacker watching the same user re-appear).

**Guard rails.**
- Caller must have completed onboarding (`UserOnboardingGuard`) — otherwise 404 USER_002.
- `rawLatitude` and `rawLongitude` are *both* required when going `READY` — half-set rejects
  with 400 `PRES_002`. Going `NOT_READY` clears them.

**Endpoints**

| Method   | Path               | OperationId                                                |
|----------|--------------------|------------------------------------------------------------|
| `GET`    | `/api/presence/me` | `getMyPresence` (auto-creates NOT_READY row on first call) |
| `PUT`    | `/api/presence/me` | `updateMyPresence` (raw lat/lng → jittered server-side)    |
| `DELETE` | `/api/presence/me` | `clearMyPresence` (NOT_READY, clears location + mood)      |

**Cross-module SPI**: exposes `PresenceLookup` — `isReady`, `findReadyVisible`, and
`findReadyCandidatesInBounds` (the bounding-box query). discovery + ping read presence through it; no
module imports presence directly.

**Entities**: `UserPresence` (embeds `JitteredLocation` record with `latitude` + `longitude`
doubles). **Tables**: `user_presences` (PK `user_id`, composite index
`(status, latitude, longitude)` so discovery's bounds query hits the index without scanning).

---

### mood-board

**What it does.** Stores the visual half of each user's profile — exactly **6 Unsplash images**
the user curates to express their current vibe. Shown to others when they tap the user's pin on
the map.

**Why it exists.** PRODUCT_CONCEPT §9: the mood board *is* the user's vibe signal. Six images
is enough to communicate aesthetic without being a full Instagram-style feed. **Binaries are
never hosted by Coffeetamine** — we store references (URL + Unsplash photo ID + attribution
metadata) and the mobile client fetches images straight from Unsplash CDN. Why: zero hosting
cost, zero copyright risk, free CDN, and the attribution links go back to the photographer per
Unsplash's terms.

**How it works.**

1. **Picker (mobile).** User searches Unsplash's API from the mobile app, picks 6 photos. Each
   pick gives mobile the photo ID, image URL, photographer name, photographer profile URL, and
   attribution (canonical photo page) URL.
2. **Replace (`PUT /api/users/me/mood-board`).** Mobile sends all 6 refs in one request. Server
   validates: count is exactly 6, every URL host ends with `unsplash.com`, then atomically
   swaps the JSONB `images` array on the user's `mood_boards` row.
3. **Display.** When someone else taps this user's pin, `GET /api/discovery/users/{userId}`
   returns the mood-board summary as part of the detail view. Mobile renders the 6 images +
   attribution.

**Storage trick.** Images are stored as a JSONB array of `MoodBoardImageRef` records (Java
records, serialised by Jackson natively via Hibernate's `SqlTypes.JSON` adapter). One row per
user — replace == array swap, no per-image rows to manage.

**Onboarding integration.** The `common.spi` `MoodBoardWriter` impl delegates to
`MoodBoardService.replaceUserMoodBoard` — same validation, same JSONB swap, called inside the user
onboarding transaction. The input shape is `common.spi.MoodBoardImageInput`, so the user module isn't
coupled to mood-board's package.

**Endpoints**

| Method | Path                             | OperationId                             |
|--------|----------------------------------|-----------------------------------------|
| `GET`  | `/api/users/me/mood-board`       | `getMyMoodBoard`                        |
| `PUT`  | `/api/users/me/mood-board`       | `replaceMyMoodBoard` (exactly 6 images) |
| `GET`  | `/api/users/{userId}/mood-board` | `getUserMoodBoard`                      |

`getMyMoodBoard` may return < 6 images only during onboarding (auto-created empty row before
the wizard finishes). `replaceMyMoodBoard` requires onboarding completed — 404 USER_002
otherwise.

**Cross-module SPI**: exposes `MoodBoardLookup` (the 6 images for discovery's detail view) and
`MoodBoardWriter` (onboarding writes).

**Entities**: `MoodBoard`. **Tables**: `mood_boards` (PK `user_id`, JSONB `images` column).

---

### discovery

**What it does.** Powers the **map view**. Given the caller's current location and search
preferences, returns a ranked list of nearby Ready users with their compatibility scores and
distance. Also serves the **detailed pin tap** — full profile + interests + mood board for one
specific user.

**Why it exists.** This is where the product happens: the user opens the app, sees pins of
people near them with compatible vibes, taps one, sees their board, decides to ping. All other
modules feed into discovery — it's the only place that joins presence, profile, interests, and
mood board into a single read.

**How `GET /api/discovery/nearby` works** (the main flow):

1. **Caller guard.** Caller must be `READY` with a location set — otherwise 409
   `PRES_001`. Discovery is reciprocal: you have to be on the map to see the map.
2. **Bounding box.** Server computes a lat/lng rectangle around the caller's jittered
   coordinates of half-side `effectiveRadius / 111_320m` for lat, adjusted by cos(lat) for lng.
   This is the cheap SQL pre-filter — uses the `(status, latitude, longitude)` index on
   `user_presences`.
3. **Refine by Haversine.** All candidates inside the rectangle get their true distance
   computed by the Haversine formula. Anyone outside `effectiveRadius` is dropped.
4. **Compatibility scoring.** For each survivor, server fetches their interest IDs (batched —
   single SQL round-trip via `userInterestsLookup.interestIdsFor(List)`). Computes
   `overlap / max(callerTags, candidateTags) * 100`, floored at 1. Candidates below
   `effectiveThreshold` are dropped.
5. **Sort.** Compatibility desc, then distance asc. No fallback — if nobody passes the filter,
   the response is empty. Mobile UX: "expand search" = client retries with larger radius.
6. **Paginate.** Spring `Pageable` slices the result; mobile fetches more pages on scroll.

**Server-side clamping** of caller params (so the schema bounds aren't the only safety).
`radiusMeters` and `minCompatibility` are *requested* by the client but **clamped** against
`coffeetamine.discovery.*` before use:

| Property                    | Default | Purpose                                       |
|-----------------------------|---------|-----------------------------------------------|
| `default-radius-meters`     | 1000    | Used when client omits the param              |
| `max-radius-meters`         | 5000    | Hard ceiling — radius >5000 clamps down       |
| `default-min-compatibility` | 30      | Used when client omits the param              |
| `min-allowed-compatibility` | 10      | Floor — `minCompatibility=1` clamps up to 10  |

Override via env vars (`COFFEETAMINE_DISCOVERY_*`). The floor exists because §7 says all three
filter conditions must be meaningful — a threshold of 1 would let everyone match.

**`GET /api/discovery/users/{userId}` — pin tap.** Composes one user's view through the `common.spi`
ports:
- public profile (name, avatar, bio) — `UserProfileLookup`
- jittered location — `PresenceLookup`
- ordered interest list — `UserInterestsLookup`
- 6 mood-board images — `MoodBoardLookup`
- compatibility score + distance relative to the caller

If the target is no longer `READY` (went offline between map render and pin tap), returns 404 —
mobile should remove the pin and show a "they just went offline" toast.

**Endpoints**

| Method | Path                            | OperationId                   |
|--------|---------------------------------|-------------------------------|
| `GET`  | `/api/discovery/nearby`         | `listNearbyUsers` (paginated) |
| `GET`  | `/api/discovery/users/{userId}` | `getDiscoveryUserDetail`      |

**No entities, no tables.** Discovery is a pure read-side aggregator: it composes presence, profile,
interests and mood-board on demand through the `common.spi` `*Lookup` ports (impls in the owner
modules). No persisted state of its own; the bounding-box query lives in presence behind
`PresenceLookup.findReadyCandidatesInBounds`.

---

### ping

**What it does.** Implements the **only interaction primitive** in MVP: User A taps a pin and
sends a *ping* to User B. If B has also pinged A (mutual ping), that becomes a **match**. No
chat, no messages, no swipes — just a signal of mutual interest, and what happens after is up
to the humans.

**Why it exists.** PRODUCT_CONCEPT §11 + §14: the product is intentionally low-friction.
Removing chat removes the "now I have to start a conversation" anxiety. A ping is a binary
"I'd be interested in meeting you" — a match is two of those crossing. Post-match state is
undefined for MVP (probably "go say hi in person, you're in the same area").

**How it works.**

1. **`POST /api/pings`** — A sends a ping to B. Server runs the guard chain:
   1. Self-ping (A == B) → `400 PING_003`
   2. A hasn't completed onboarding → `404 USER_002`
   3. B is not `READY` → `409 PING_001`
   4. A PENDING ping already exists for the `(A, B)` unordered pair → `409 PING_002`
      - If the existing ping is B→A (inverse), the caller should `pong` instead.
      - If it's A→B (already sent), nothing to do.

   On success, server inserts a `PingInteraction` row in `PENDING` state, publishes
   `PingSentEvent`, returns the ping. The notification module picks up the event
   and creates an in-app notification for B (with future push to B's device).

2. **`POST /api/pings/{pingId}/pong`** — B taps "pong" on the notification. Server resolves the
   ping to `MATCHED`, creates a `UserMatch` row, publishes `PingMatchedEvent`. Both
   A and B get a "match created" notification.

3. **`DELETE /api/pings/{pingId}`** — A withdraws a pending ping. State → `WITHDRAWN`,
   `PingWithdrawnEvent` published. B's notification gets a "withdrawn" flag so the mobile
   client can grey it out.

4. **Reads.** `/sent` and `/received` are paginated inbox views, filterable by status.
   `/matches` is the user's match list — peer profile summaries enriched via the `common.spi`
   `UserProfileLookup` port.

**Idempotency** (the load-bearing trick). Two mechanisms, both at the DB level:

- **Pair normalisation.** `UnorderedPair.of(a, b)` sorts UUIDs by `compareTo`, so `(A,B)` and
  `(B,A)` always store as the same `(pair_low_id, pair_high_id)`. The `pings` table has a
  **partial unique index** on that pair where `status='PENDING'` — concurrent
  `sendPing(A→B)` and `sendPing(B→A)` cannot both succeed; one wins, the loser catches
  `DataIntegrityViolationException` and gets a 409.
- **Match dedupe.** `user_matches` has a unique constraint on the same unordered pair. If two
  pongs race (unlikely but possible), the loser catches the IntegrityViolation and re-fetches
  the winner's match row. Net effect: **exactly one match per pair, forever**, with no
  application-level lock or coordination service.

**Endpoints**

| Method   | Path                       | OperationId                                       |
|----------|----------------------------|---------------------------------------------------|
| `POST`   | `/api/pings`               | `sendPing`                                        |
| `GET`    | `/api/pings/sent`          | `listSentPings` (paginated, filter by `status`)   |
| `GET`    | `/api/pings/received`      | `listReceivedPings`                               |
| `DELETE` | `/api/pings/{pingId}`      | `withdrawPing` (sender only)                      |
| `POST`   | `/api/pings/{pingId}/pong` | `pongPing` (recipient only — creates `UserMatch`) |
| `GET`    | `/api/matches`             | `listMatches` (paginated)                         |
| `GET`    | `/api/matches/{matchId}`   | `getMatch`                                        |

**Entities**: `PingInteraction`, `UserMatch`. **Tables**: `pings`, `user_matches`.

---

### notification

**What it does.** When something happens that the user should know about (received a ping,
matched with someone), this module:

1. Writes a row to the **in-app inbox** (visible at `GET /api/notifications`).
2. **Pushes** to the user's registered device(s) via FCM/APNs (currently stubbed —
   `LoggingPushDispatcher` writes to logs).

It also owns the **device registry** — mobile apps register their FCM/APNs token here on login
so push has a destination.

**Why it exists.** Ping/match flows are async — A pings B, but A might be in their pocket, and
B's only signal to come back to the app is the notification. Without a push, the product loop
breaks. The in-app inbox is the durable record so users can catch up on what they missed.

**How it works.**

1. **Event arrives.** Ping module publishes `PingSentEvent` / `PingMatchedEvent`. Spring Modulith
   delivers each to `NotificationEventListener`'s `@ApplicationModuleListener` methods, async, after
   the publisher's transaction commits.
2. **Idempotency guard.** Before inserting, the handler does a Spring Data `existsBy` check on the
   notification's natural key (recipient + type + ping/match id) and skips if a row already exists.
   The Modulith registry gives at-least-once delivery; this keeps a redelivery from duplicating the
   row. Peer name/avatar come from the `common.spi` `UserProfileLookup` port (read synchronously, impl
   in `user`) and are denormalised onto the row.
3. **Persist in-app row.** Handler builds a `Notification` (denormalised peer data: peer's
   name + avatar are copied so the inbox can render without joining user/profile).
4. **Dispatch push (best effort).** `PushDispatcher.dispatch(notification)` is called inside a
   try/catch. If push fails, the in-app row stays — the user will still see it when they open
   the app. Push failure does **not** roll back the notification.
5. **Mobile reads / acks.** `GET /api/notifications` paginates the inbox; mobile flips
   `is_read` by calling `/api/notifications/{id}/read` or bulk-clears with `/read-all` (single
   SQL UPDATE — no N+1).

**Device registration.** When the mobile app boots, it sends `POST /api/notifications/devices`
with `{ platform, deviceToken }`. Upsert keyed by `(platform, deviceToken)` — re-registering
the same token is a no-op. On logout, mobile should `DELETE` the device. Devices are stored as
JSONB array on the `user_devices` row (one row per user).

**Push status.** `PushDispatcher` interface is wired and ready. Only shipped impl is
`LoggingPushDispatcher`. Real FCM + APNs integration is V2 — see
[`MVP_SCOPE_CUTS.md §2`](architecture/MVP_SCOPE_CUTS.md#2-real-push-delivery-fcm--apns--deferred)
for the contract that's stable today and the work needed to plug in real dispatchers.

**Endpoints**

| Method   | Path                                       | OperationId                                           |
|----------|--------------------------------------------|-------------------------------------------------------|
| `GET`    | `/api/notifications`                       | `listNotifications` (paginated, filter by `isRead`)   |
| `GET`    | `/api/notifications/unread-count`          | `getUnreadNotificationCount`                          |
| `PUT`    | `/api/notifications/{notificationId}/read` | `markNotificationRead`                                |
| `PUT`    | `/api/notifications/read-all`              | `markAllNotificationsRead` (single bulk UPDATE)       |
| `GET`    | `/api/notifications/devices`               | `listMyDevices`                                       |
| `POST`   | `/api/notifications/devices`               | `registerDevice` (upsert by `platform + deviceToken`) |
| `DELETE` | `/api/notifications/devices/{deviceId}`    | `unregisterDevice`                                    |

**Entities**: `Notification`, `UserDevices` (JSONB `devices` column of `DeviceRecord` records).
**Tables**: `notifications` (with a partial index on `(user_id, created_at desc) where is_read=false`
so the unread inbox query is index-only), `user_devices`.

---

## Authentication

Stateless JWT, provider-agnostic OIDC. Backend is an OAuth2 Resource Server validating
tokens via the issuer's JWKS at `${OIDC_ISSUER_URI}/.well-known/openid-configuration`
and Spring Security's built-in `aud` claim check against `${OIDC_AUDIENCE}`.
Mobile clients use OIDC Authorization Code + PKCE; for Google login the OIDC provider
brokers a federated Google IdP — the mobile app never talks to Google directly.

- The JWT `sub` is an opaque string — **not** required to be a UUID. The
  `external_identities` table maps `(issuer, subject) → app_user_id` (a server-minted UUID),
  JIT-provisioned on first authenticated request by `UserIdentityResolver` (Caffeine-cached).
- Principal = internal `app_user_id` UUID — accessible via `SecurityUtils.getCurrentUserId()`.
- Authorities: every authenticated principal gets `ROLE_USER`; `ROLE_ADMIN` is granted only
  when the JWT `email` claim matches the backend-owned `app.admin-emails` allowlist
  (`APP_ADMIN_EMAILS` env var). IdP role claims are ignored.
- No local table for the JWT identity — per-domain rows key on the internal `app_user_id`
  UUID without DB-level FKs to the identity provider.

Full mobile-auth setup: [`architecture/MOBILE_OIDC_GOOGLE.md`](architecture/MOBILE_OIDC_GOOGLE.md).

Local dev uses `ghcr.io/navikt/mock-oauth2-server` at `http://localhost:8081/default` —
auto-started by `make up`, no admin UI or realm import required. `make token` /
`make token-admin` mint signed JWTs directly from the mock server.

---

## Configuration

All operational knobs live in `main/src/main/resources/application.yml`. Profile-specific
overrides go in `application-{profile}.yml`. Default profile is `dev`.

| Key                                        | Default                                     | Purpose                            |
|--------------------------------------------|---------------------------------------------|------------------------------------|
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD`   | local Postgres                              | App database                       |
| `MANAGEMENT_SERVER_PORT`                   | 9090 (prod profile)                         | Actuator port — scrape-side only   |
| `OIDC_ISSUER_URI`                          | `http://localhost:8081/default`             | JWT validation (JWKS discovery)    |
| `OIDC_AUDIENCE`                            | `coffeetamine-api`                          | Expected `aud` claim               |
| `APP_ADMIN_EMAILS`                         | empty                                       | Email allowlist for `ROLE_ADMIN`   |
| `COFFEETAMINE_DISCOVERY_DEFAULT_RADIUS`    | 1000                                        | Default search radius (m)          |
| `COFFEETAMINE_DISCOVERY_MAX_RADIUS`        | 5000                                        | Hard ceiling (m)                   |
| `COFFEETAMINE_DISCOVERY_DEFAULT_THRESHOLD` | 30                                          | Default compatibility threshold    |
| `COFFEETAMINE_DISCOVERY_MIN_THRESHOLD`     | 10                                          | Compatibility floor (§7 invariant) |
| `COFFEETAMINE_PRESENCE_JITTER_METERS`      | 80                                          | GPS jitter radius (§10 invariant)  |
| `TRACING_SAMPLING`                         | 1.0                                         | Brave sampling probability         |

---

## Tech stack

- **Java 25** with virtual threads enabled (`spring.threads.virtual.enabled=true` in dev)
- **Spring Boot 4.1** with Spring Security 7, Spring Data JPA, Spring Modulith 2.1
- **Hibernate 7** (JSONB columns native via Jackson, batch_size=25, UTC time-zone normalisation)
- **Liquibase** — schema-as-code, conventions in [`architecture/LIQUIBASE_CONVENTIONS.md`](architecture/LIQUIBASE_CONVENTIONS.md)
- **OIDC** provider-agnostic (local dev: `mock-oauth2-server`; production: ZITADEL /
  Keycloak / any spec-compliant issuer — Google as end-user IdP federates through the
  provider, not the backend)
- **Spring Modulith** for in-process domain events — durable JDBC event publication registry,
  idempotent consumers, per-module observability (`/actuator/modulith` + Micrometer)
- **PostgreSQL 17** (single app DB; identity mapping lives in `external_identities`)
- **OpenAPI 3** specs in `openapi/` — generated to API stubs per module via
  `openapi-generator-maven-plugin` (`apiPackage=ua.coffeetamine.api.<module>`)
- **MapStruct** for DTO ↔ entity mapping
- **springdoc-openapi** for Swagger UI

**Build / quality plugins**: Spotless (Google Java Format, auto-applies on `process-sources`),
Checkstyle (warning-only), ArchUnit (`LayerDependencyTest` DDD layering) + Spring Modulith
(`ApplicationModules.verify()` cross-module boundaries) — both *fail* the build on violation,
Mockito agent attach, Failsafe (`*IT.java` integration tests).

Parallel build enabled: `.mvn/maven.config` sets `-T 1C` + Maven build cache.

---

## Architecture invariants

Cross-cutting rules — `CLAUDE.md` is the source of truth, this is a recap for the README:

1. **Cross-module via `common` only** — reads through synchronous `common.spi` ports (impl in the
   owner module), reactions through async `common.event` (ping → notification); never import another
   module. Verified by `ApplicationModules.verify()` (`ModularityTests`).
2. **No FKs across module boundaries** — identity FKs point to the internal `app_user_id`
   UUID (resolved by `UserIdentityResolver` from the JWT `(iss, sub)` tuple; no local
   per-feature `users` table). Single-module FKs are fine
   (`user_interest_selections.interest_id`).
3. **Errors leave the platform as RFC 7807** with a stable `errorCode` for client dispatch.
   Throw `NotFoundException`/`BadRequestException`/etc, never `ResponseStatusException`.
4. **Onboarding is atomic** — `user` writes profile + interests + mood-board in one transaction via
   the `common.spi` `UserInterestsWriter` / `MoodBoardWriter` ports.
5. **Location is jittered server-side** — raw GPS never persists, never returns.
6. **Match detection is idempotent at the DB level** — unique constraint on the unordered pair.
7. **Compatibility score = tag overlap, normalised 1..100** — single formula, no abstraction
   until a second scorer lands (see scope cuts).

---

## Reference docs

- [`PRODUCT_CONCEPT.md`](PRODUCT_CONCEPT.md) — full product spec, source of truth for domain
  rules
- [`CLAUDE.md`](CLAUDE.md) — project-specific architecture rules + conventions (Claude Code reads
  this on every session; humans should too)
- [`architecture/MOBILE_OIDC_GOOGLE.md`](architecture/MOBILE_OIDC_GOOGLE.md) — mobile
  OIDC auth flow + provider-specific notes for Google as a federated IdP
- [`architecture/LIQUIBASE_CONVENTIONS.md`](architecture/LIQUIBASE_CONVENTIONS.md) — schema
  evolution rules
- [`architecture/MVP_SCOPE_CUTS.md`](architecture/MVP_SCOPE_CUTS.md) — what we *did not* build
  for MVP and the trigger that pulls each back into scope
- [`openapi/`](openapi) — OpenAPI 3 specs, one per module (the wire contract is the schema, not
  hand-written controllers)
- [`Makefile`](Makefile) — all daily commands (`make help`)

When code disagrees with `PRODUCT_CONCEPT.md`, the doc wins. When the doc disagrees with reality
because we've changed our mind, update the doc.
