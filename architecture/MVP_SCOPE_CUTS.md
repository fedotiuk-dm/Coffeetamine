# MVP Scope Cuts

> Why this exists: every line below is something we *consciously* did not build for MVP. When a
> future developer thinks "let me just add a quick `CompatibilityScorer` interface" — read this
> first. Each cut explains both **what** we deferred and the **trigger** that should pull it
> back into scope.

## 1. CompatibilityScorer interface — deferred

**Current state.** `DiscoveryServiceImpl.compatibilityScore(Set, Set)` is a `private static`
method computing tag-overlap normalised to 1–100. There is one implementation and no abstraction.

**Why deferred.** Project parent `CLAUDE.md`: *"Keep the scoring function behind a single
interface so it can be swapped for embeddings later — but **don't build the abstraction layer
until the second formula actually lands**."* Premature abstraction adds indirection without
benefit when the abstraction has a single implementation.

**Pull-back trigger.** When `PRODUCT_CONCEPT.md §15` ("advanced similarity (embeddings)") lands
— the moment we have two scorers, extract `CompatibilityScorer` interface, make the existing
overlap formula `TagOverlapScorer`, and inject via Spring profile or config flag.

**Test for premature abstraction.** Ask "do I have a second concrete scorer to plug in?" If no
— don't extract.

## 2. Real push delivery (FCM + APNs) — deferred

**Current state.** `PushDispatcher` is an interface in `notification/.../service/`. Only
implementation is `LoggingPushDispatcher` which writes the notification to the application log.
`UserDevices` schema stores `platform` + `deviceToken` ready for a real dispatcher.

**Why deferred.** Real delivery requires:

- Firebase service-account JSON (production) — not just a code change, an ops/secret-management
  task.
- APNs `.p8` private key + team ID + bundle ID — Apple Developer account integration.
- Mobile clients capable of presenting device tokens — the mobile app itself is V2.
- Retry/backoff strategy, per-platform fanout, token rotation handling on `Unregistered` errors.

None of this is "polish the MVP" — it's a real third-party integration that wants its own
sprint with credentials provisioning.

**Pull-back trigger.** Mobile-app team is ready to test end-to-end push. Add `FcmPushDispatcher`
+ `ApnsPushDispatcher` behind a `@ConditionalOnProperty(coffeetamine.notification.push-driver)`
selector; keep `LoggingPushDispatcher` as the default for dev and integration tests.

**Contract is stable.** `PushDispatcher.dispatch(Notification)` already exists — adding real
dispatchers will not require touching `NotificationEventListener`.

## 3. Coffee POI layer (§13) — deferred

**Current state.** No `poi` module, no `coffee_spots` table. Discovery uses jittered user
location only.

**Why deferred.** `PRODUCT_CONCEPT.md §13` explicitly frames coffee as a *soft anchor*: *"Coffee
is a contextual anchor, not a strict venue system... Not required for interaction."* Users today
type their hangout into the `mood` text field on `user_presences`. The product loop closes
without a structured POI layer.

**Pull-back trigger.** Either of:

1. Product wants map clustering around real venues (§15 "real-time coffee spot clustering").
2. Compatibility ranking needs to factor "users at the same cafe" — currently impossible
   without POI references.

When pulled back: new bounded context `poi/` (entities `CoffeeSpot`, integration ports
`CoffeeSpotLookup`), Liquibase under `1.x.0/`, separate Google Places ingestion pipeline.

## 4. Integration tests (`*IT.java`) — deferred by user choice

**Current state.** Architecture tests (ArchUnit, 7 classes) exist. No service or controller
integration tests yet. Failsafe + Testcontainers are wired in the parent POM and ready.

**Why deferred.** User explicitly chose to validate MVP logic first, write tests after.

**Pull-back trigger.** Before the first non-dev deployment. Highest-priority targets, in order:

1. **Ping concurrency** — concurrent A→B and B→A pings produce exactly one `UserMatch` (relies
   on a partial unique index in `pings` + unique constraint in `user_matches`).
2. **Onboarding atomicity** — failing mood-board write rolls back the profile + interest writes.
3. **Discovery clamping** — caller-supplied `radius=1_000_000` and `minCompatibility=0` are
   forced into the `coffeetamine.discovery.*` allowed range.
4. **Presence jitter** — raw GPS submitted never appears in any API response.
5. **Notification listener idempotency** — same `PingSentEvent` redelivered twice produces one
   `Notification` row.

## 5. Liquibase rollback blocks — deferred

**Current state.** Changesets are forward-only.

**Why deferred.** See `LIQUIBASE_CONVENTIONS.md` rationale — additive migrations + careful
staging cover most real failure modes. Rollback DSL fights you on anything beyond `dropTable`.

**Pull-back trigger.** First data migration on a populated production table.

## 6. Multiple Spring profiles for the API surface

**Current state.** `dev` profile is the default and only profile with config overrides.
`application.yml` has env-var holes for everything that matters (DB, OIDC).

**Why deferred.** A `prod` profile today would just duplicate `application.yml` with no
overrides. Wait until prod has concrete differences (Hikari pool size, logging level, tracing
sampling) — then introduce `application-prod.yml` with only those keys.

**Pull-back trigger.** Production deploy needing non-dev defaults — typically when traffic is
high enough that tracing-sampling-1.0 burns money.

---

**Reading order if you arrive here cold.** Start with `PRODUCT_CONCEPT.md` for the product loop,
`CLAUDE.md` for the architecture conventions, then this file for "what is the project *not*
doing and why". Then read code.
