# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

> Parent file: `../CLAUDE.md` (workspace-level rules — Maven/pnpm commands for the reference
> stack, Spring Boot conventions, DDD layering, OpenAPI-First flow). The rules below are
> **coffeetamine-specific** and assume the parent is already loaded.

## Product — Coffeetamine

Full spec: `PRODUCT_CONCEPT.md`. TL;DR:

Location-based social discovery for **vibe-based coffee micro-encounters**. Not dating,
not messaging. Loop: Google login → onboarding wizard → set status (Ready / Not Ready +
optional mood) → appear on map → tap nearby user pin → view their mood board (6 Unsplash
images) + interests → send **ping** → mutual ping = **match**. No chat in MVP.

Treat the concept doc as the source of truth for domain rules. If the code disagrees
with `PRODUCT_CONCEPT.md`, the doc wins unless the user explicitly says otherwise.

## Code Quality Tooling

| Tool                                             | What it does                                                                                                                                                                                                         | Failure mode                                                                                                        |
|--------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------|
| **Spotless** + Google Java Format                | Auto-applies formatting on `process-sources`. Also sorts `pom.xml`.                                                                                                                                                  | Fails build if `mvn verify` runs and code is unformatted — `mvn spotless:apply` to fix. Skipped under `-Pdev-fast`. |
| **Checkstyle**                                   | Naming, complexity, anti-patterns (no `System.out`, no `printStackTrace()`, method ≤ 80 lines, params ≤ 8, etc.). Config: `checkstyle.xml` + `checkstyle-suppressions.xml`.                                          | Warning-only — never fails the build.                                                                               |
| **Mockito agent** (`mockito-maven-plugin`)       | Auto-attaches the Mockito agent so `mockito-inline` works under Java 21+ without `-Xshare:off` hacks.                                                                                                                | Surefire/Failsafe inherit `@{argLine}` automatically.                                                               |
| **Failsafe**                                     | Runs `*IT.java` integration tests in the `verify` phase.                                                                                                                                                             | Bound to `integration-test` + `verify` goals.                                                                       |
| **ArchUnit** (`main/src/test/.../architecture/`) | `LayerDependencyTest` (DDD layering — controllers don't touch repos, domain doesn't depend on web/mapper, etc.). | Surefire — fails the build on architecture violation.                                                               |
| **Spring Modulith** (`@ApplicationModule` + `ModularityTests`) | `ApplicationModules.verify()` enforces module boundaries — no module imports another. Cross-module **reads** go through synchronous `common.spi` ports (interface in `common`, impl in the owner module, wired by Spring); cross-module **reactions** go through events on the durable JDBC registry (ping → notification, async). Per-module load at `/actuator/modulith` + Micrometer. | Surefire — `verify()` fails the build on a boundary violation. |
| **Parallel build** (`.mvn/maven.config`)         | `-T 1C` (1 thread per CPU core) + Maven build cache enabled.                                                                                                                                                         | Reactor respects the module dependency graph — `main` builds last.                                                  |

## Error Handling — RFC 7807

All errors leave the platform as RFC 7807 `application/problem+json`. The contract is
shared by both the global controller advice and the security filter chain, so clients
parse one shape regardless of which layer rejected the request.

| Component                                                                                                      | Lives in            | Purpose                                                                                                                                                                                                                   |
|----------------------------------------------------------------------------------------------------------------|---------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `ErrorCode` enum                                                                                               | `common/exception/` | Stable, machine-readable codes (`AUTH_001`, `USER_001`, …). Mobile client may dispatch on them. Add new codes per domain as features land.                                                                                |
| `ProblemDetails` factory                                                                                       | `common/exception/` | Builds Spring `ProblemDetail` with a stable `type` URI, `errorCode` property, optional `traceId` (from MDC) and `instance` (request path).                                                                                |
| `NotFoundException`, `BadRequestException`, `ConflictException`, `ForbiddenException`, `UnauthorizedException` | `common/exception/` | Throwable wrappers around `ErrorResponseException` — pre-built ProblemDetail travels with the exception. Throw from services / domain.                                                                                    |
| `GlobalExceptionHandler`                                                                                       | `common/exception/` | `@RestControllerAdvice` translating framework exceptions (validation, type-mismatch, missing param, JSON parse, auth, data integrity, 404, fallback `Exception`) into ProblemDetails. Auto-discovered via component scan. |
| `ProblemDetailSecurityHandler`                                                                                 | `common/web/`       | Filter-chain analogue: handles 401 (no/invalid JWT) and 403 (insufficient role) using the same `ProblemDetails` factory. Mounted from `SecurityConfig`.                                                                   |

Throw `new NotFoundException("UserProfile", id)` instead of `ResponseStatusException(404, …)` — that's the only way the response will carry an `errorCode` clients can dispatch on.

## Local OIDC Dev Provider

`docker-compose.dev.yml` starts `ghcr.io/navikt/mock-oauth2-server:2.1.10` at
`http://localhost:8081/default`. It is a lightweight, spec-compliant OIDC IdP — no
admin UI, no realm import, no separate Postgres. The container is configured to mint
JWTs whose `aud` claim is `coffeetamine-api`, matching the backend's audience
validator (see `spring.security.oauth2.resourceserver.jwt.audiences` in
`application.yml`).

Fetch a dev JWT via `make token` (USER) or `make token-admin` (USER + ADMIN — the
latter sets the email to `admin@coffeetamine.local`, which matches the
`APP_ADMIN_EMAILS` allowlist baked into the compose file).

Production uses a real OIDC provider (ZITADEL / Keycloak managed by DevOps) — the
backend is provider-agnostic and only needs:

- `OIDC_ISSUER_URI` — discovers JWKS at `{issuer}/.well-known/openid-configuration`
- `OIDC_AUDIENCE` — expected `aud` claim
- `APP_ADMIN_EMAILS` — comma-separated email allowlist for `ROLE_ADMIN`

See `architecture/MOBILE_OIDC_GOOGLE.md` for the mobile OIDC integration flow.

Tests are split: `*Test.java` (unit, Surefire, `mvn test`) vs `*IT.java` (integration,
Failsafe, `mvn verify`). Use Testcontainers in `*IT.java`.

## Project Layout

Multi-module Maven, mirrors the pattern of `../backend/`. Each feature module is a
bounded context; `main` is the bootstrap that pulls every module as a dependency so
Spring Boot's default component / entity / repository scan covers `ua.coffeetamine.*`
without any explicit `@ComponentScan` / `@EntityScan`.

```
forgejo/
├── pom.xml                  # parent: Spring Boot 4.1, Java 25, common deps + plugins
├── common/                  # security helpers (SecurityUtils), base classes, web adapters
├── user/                    # profile (name, avatar, bio, onboarding state) keyed by app_user_id UUID
├── interests/               # fixed global tag catalog + per-user tag selections
├── presence/                # Ready / Not Ready status, jittered location, mood comment
├── mood-board/              # 6 Unsplash image refs per user (no binary hosting)
├── discovery/               # nearby-user queries + compatibility scoring
├── ping/                    # ping/pong + match detection (no chat)
├── notification/            # outbound push (FCM/APNs) + in-app inbox
├── main/                    # @SpringBootApplication, SecurityConfig, application.yml, Liquibase master
└── architecture/
    └── MOBILE_OIDC_GOOGLE.md
```

**Adding a new module:** 1) create the directory with its `pom.xml` extending the root
parent, 2) declare it in the root `<modules>` list, 3) add it as a dependency in
`main/pom.xml` (otherwise Spring won't see its beans), 4) keep its code under
`ua.coffeetamine.<module>` so component scan picks it up, 5) add a `package-info.java` with
`@ApplicationModule` on the base package — never import another module's package; to **read** another
module's data add a port to `common.spi` (interface in `common.spi`, impl in your module), to **react**
to something publish / consume a `common.event` record, 6) add a Liquibase changelog
include in `main/src/main/resources/db/changelog/coffeetamine-changelog.yaml`.

## Per-module DDD Layering

Each feature module follows the layering documented in `../CLAUDE.md`:

```
<module>/
├── domain/
│   ├── model/         # JPA entities (rich domain logic)
│   └── repository/    # Spring Data + JPA Specifications
├── service/           # interface + Impl
├── mapper/            # MapStruct (thin)
├── web/
│   ├── controller/    # thin delegator only
│   └── dto/           # only when not generated from OpenAPI
└── config/            # module-specific Spring config
```

All feature modules are populated — each contains its domain model, repository, service,
mapper, web, and config packages following the layering above. When extending a module,
add code inside the matching layer; don't create new top-level packages.

## Inter-module Collaboration — SPI reads + event reactions

Modules never import another module's package (`verify()` enforces it). They collaborate two ways:

**Reads — synchronous SPI** (`common.spi`). When a module needs another's data it calls a port
interface in `common.spi` (`UserProfileLookup`, `PresenceLookup`, `UserInterestsLookup`,
`MoodBoardLookup`, + the `*Writer` ports). The impl lives in the **owner** module
(`user/service/UserProfileLookupImpl`, …) and Spring injects it at runtime — so the consumer compiles
against `common` only, with **no inter-module pom dependency**. discovery composes its nearby/detail
views this way; ping checks readiness + enriches match peers; onboarding writes interests + mood-board
atomically inside the user transaction.

**Reactions — async events** (`common.event`). For "X happened, react over there" a module publishes a
`DomainEvent` via `ApplicationEventPublisher`; consumers handle it with `@ApplicationModuleListener`.
Delivery is async-after-commit and durable via the **JDBC event publication registry**
(`spring-modulith-starter-jdbc`; tables `event_publication` + `event_publication_archive` via
Liquibase, `completion-mode: archive`, `republish-outstanding-events-on-restart`). `@EnableAsync` is on
`CoffeetamineApplication`. The only event flow today: `ping` publishes `PingSentEvent` /
`PingMatchedEvent` → `notification` reacts (reads the peer profile via the `UserProfileLookup` SPI,
idempotent via Spring Data `existsBy`). No message broker — events are in-process.

`common` is OPEN, so any module may use its `spi` ports + `event` records; there are **no
`integration/` packages**. The `UserOnboardingGuard` SPI follows the same shape (interface in
`common`, impl in `user`). Boundaries verified by `ModularityTests` (`verify()`); per-module load at
`/actuator/modulith` + `/actuator/prometheus`.

Boundaries are verified by `ModularityTests` (`ApplicationModules.verify()`); the live module graph
and per-module load are at `/actuator/modulith` and `/actuator/prometheus`.

## Authentication — OIDC Resource Server

Stateless JWT via any OIDC provider. Mobile clients use **OIDC Authorization Code + PKCE**;
when Google is the end-user IdP it is federated through the OIDC provider — the mobile app
never talks to Google directly.

**Full flow + provider setup + mobile-side AppAuth integration:**
[`architecture/MOBILE_OIDC_GOOGLE.md`](architecture/MOBILE_OIDC_GOOGLE.md).

Backend-side contract (already wired up in `main/.../config/SecurityConfig.java`):

- Validation: OAuth2 Resource Server → JWKS at
  `${OIDC_ISSUER_URI}/.well-known/openid-configuration`, plus Spring Security's built-in
  audience check from `${OIDC_AUDIENCE}`.
- `SecurityUtils.getCurrentUserId()` returns the internal `app_user_id` UUID, mapped from
  the JWT `(iss, sub)` tuple via `UserIdentityResolver` (Caffeine-cached, JIT-provisioned
  on first authenticated request — see `common/identity/`). The JWT subject itself is an
  opaque string; it is **not** required to be a UUID.
- Authorities: every authenticated principal gets `ROLE_USER`; `ROLE_ADMIN` is granted only
  when the JWT `email` claim matches the backend-owned `app.admin-emails` allowlist. The
  IdP's role claims are intentionally ignored.
- No local `users` table for the JWT identity. Per-domain profile tables key on the
  internal `app_user_id` UUID resolved by `UserIdentityResolver` from the JWT `(iss, sub)`
  tuple. The `external_identities` table stores the `(issuer, subject) → app_user_id`
  mapping.

Use `SecurityUtils.getCurrentUserId()` / `isAdmin()` / `verifyOwnerOrAdmin(...)` from the
`common` module — don't re-implement JWT inspection in feature modules.

## Coffeetamine-specific Modelling Invariants

Flag to the user if you're about to deviate:

- **Location imprecision is enforced server-side.** Persist jittered offsets, or compute
  the offset deterministically per session — never echo raw GPS through the API.
- **Compatibility score** (MVP) = interest-tag overlap normalised to `[1, 100]`. Keep the
  scoring function behind a single interface so it can be swapped for embeddings later —
  but don't build the abstraction layer until the second formula actually lands.
- **Discovery filter is conjunctive**: `status == Ready` ∧ `distance ≤ radius` ∧
  `compatibility ≥ threshold`. All three must pass — no fallback ranking.
- **Ping match detection must be idempotent**. Two near-simultaneous pings must produce
  exactly one match event. Enforce with a unique constraint on the unordered `(a, b)`
  pair, not application-level checks.
- **Mood board**: exactly 6 images per user, references only (URL + Unsplash photo ID +
  attribution). Never proxy / host the binaries.

## Commands

Most daily work goes through `make` — see `make help` for the full list. Direct Maven /
Docker commands behind the targets:

```bash
# Build (all modules, runs Spotless apply on process-sources)
mvn clean install            # or: make install

# Fast build — skip tests, Spotless, Checkstyle, Javadoc
mvn clean install -Pdev-fast # or: make build-fast

# Run the application (from main/)
mvn -pl main -am spring-boot:run -Dspring-boot.run.profiles=dev   # or: make dev

# Single test
mvn test -Dtest=ClassNameTest#methodName

# Integration tests (Failsafe — class name *IT.java)
mvn verify                   # or: make test-int

# Code quality
mvn spotless:apply           # or: make format
mvn checkstyle:check         # or: make check   (warning-only, never fails build)

# Local infra (Postgres + mock-oauth2-server)
make up      # alias for docker compose -f docker-compose.dev.yml up -d
make down    # keep volumes
make reset   # wipe volumes

# Production image (this is what DevOps consumes — they do NOT use docker-compose.dev.yml)
make image   # alias for docker build -t coffeetamine-backend:local .
```

Note: no Maven wrapper (`./mvnw`) is shipped — install Maven on host, or use the Docker
build (`Dockerfile` / `Dockerfile.dev`) which is based on
`bellsoft/liberica-runtime-container:jdk-25-musl` and installs a pinned Apache Maven
binary inside the image. Liberica matches the host IDE JDK vendor, so prod and dev share
one runtime.

Local dev defaults that match `docker-compose.dev.yml`: `DB_URL=jdbc:postgresql://localhost:5432/coffeetamine`,
`DB_USERNAME=coffeetamine`, `DB_PASSWORD=coffeetamine`, `OIDC_ISSUER_URI=http://localhost:8081/default`,
`OIDC_AUDIENCE=coffeetamine-api`, `APP_ADMIN_EMAILS=admin@coffeetamine.local` — the local
OIDC provider is `mock-oauth2-server` (no admin console; no realm import needed). See
`architecture/MOBILE_OIDC_GOOGLE.md` for the mobile OIDC flow and provider-specific notes
on configuring Google as a federated IdP for production.

Per parent `CLAUDE.md`: **never run build / start / deploy commands without explicit user
approval**. Ask first.

## When in Doubt

- Domain question → re-read `PRODUCT_CONCEPT.md`.
- Architectural pattern question → consult `../backend/` for a concrete example of the
  same pattern already implemented.
- Mobile auth question → `architecture/MOBILE_OIDC_GOOGLE.md`.
- Workflow / tooling question → parent `../CLAUDE.md`.
