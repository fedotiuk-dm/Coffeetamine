# OIDC Provider Requirements

> Coffeetamine's backend is OIDC-provider-agnostic — but not literally every IdP fits.
> This document pins the contract DevOps signs the IdP up to. If the chosen provider
> ticks every box below, swapping it in is a two-env-var change (`OIDC_ISSUER_URI`,
> `OIDC_AUDIENCE`) plus a tour of the admin-allowlist setup. Anything missing is a
> design discussion, not a config tweak.

## Required from the IdP

| Capability                    | Required value / behaviour                                                                                                     | Why the backend needs it                                                                                                                                                      |
|-------------------------------|--------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| OIDC discovery document       | `GET {issuer}/.well-known/openid-configuration` returns 200 with `issuer`, `jwks_uri`, `id_token_signing_alg_values_supported` | Spring Boot's resource-server auto-configuration bootstraps off this — no manual JWKS wiring.                                                                                 |
| JWKS endpoint                 | Public keys at `jwks_uri`, rotatable, JWK Set format                                                                           | Nimbus fetches + caches keys for JWT signature verification.                                                                                                                  |
| Access tokens as JWT          | Bearer tokens issued by the IdP must be **JWS-signed JWTs**, not opaque strings                                                | Backend is a resource server; it validates locally, never calls `/introspect`.                                                                                                |
| Signing algorithm             | One of `RS256`, `RS384`, `RS512`, `ES256`, `ES384`                                                                             | Default Nimbus support, no extra dependencies.                                                                                                                                |
| Stable `sub` claim            | Immutable per `(issuer, user)` pair; never recycled across deletions                                                           | Backend maps `(iss, sub) → app_user_id` once and trusts that mapping forever. A reused `sub` would silently rebind one person's data to another.                              |
| Configurable `aud` claim      | Must be settable to `coffeetamine-api` (or whatever `OIDC_AUDIENCE` becomes)                                                   | Token-confusion guard via Spring Security's `jwt.audiences` validation. Tokens minted for any other client of the same IdP must NOT validate against this API.                |
| `email` claim in access token | Best-effort, may be empty                                                                                                      | Used for the admin-allowlist check and for populating `AuthenticatedUser`. Mandatory only if any admin is wanted.                                                             |
| `email_verified` claim        | `true` for accounts whose email has been confirmed via the IdP's standard flow                                                 | `JwtAuthorityMapper` refuses `ROLE_ADMIN` unless this is `true`. Without it, a self-service-registered user could pick an admin email at sign-up and ride the allowlist.      |
| `iss` claim consistency       | Stamped value must equal the issuer URL the backend reaches at boot time **byte-for-byte**                                     | Spring validates issuer with exact-string match. `http://oidc:8081/default` vs `http://localhost:8081/default` is two different issuers as far as the validator is concerned. |

## Optional / used opportunistically

| Claim                  | Purpose                                                                    |
|------------------------|----------------------------------------------------------------------------|
| `preferred_username`   | Display handle on `AuthenticatedUser` (falls back to nothing if absent).   |
| `given_name`, `name`   | Same — best-effort display fields.                                         |

The backend **does not** read IdP-supplied `roles` / `groups` / `realm_access` claims —
those are deliberately ignored. Authorization is gated by `app.admin-emails` only.

## Confirmed-working IdPs

| Provider                   | Status                      | Notes                                                                                                                            |
|----------------------------|-----------------------------|----------------------------------------------------------------------------------------------------------------------------------|
| `mock-oauth2-server` 2.1.x | Verified (local dev)        | Stamps `aud`, `email`, `email_verified` via `tokenCallbacks` — see `docker-compose.dev.yml`.                                     |
| ZITADEL ≥ 2.50             | Designed against, ESO model | Set the API "Audience" in the project; map `email_verified` in the action runtime if it isn't already in the access token.       |
| Keycloak 24+               | Compatible                  | Realm setting "Add audience" + a mapper for the API client. `email_verified` is on by default after the email verification flow. |
| Auth0                      | Compatible (managed)        | Set `audience` parameter on the authorize call; enable email verification in the tenant.                                         |

## Known-incompatible (without explicit work)

- **AWS Cognito** when configured to emit opaque tokens. Switching the user-pool client to
  issue JWT access tokens makes it compatible, but it isn't the default.
- **SAML-only IdPs.** No JWT issuance. Would need a SAML→JWT broker (e.g. Keycloak in front).
- **GitHub OAuth, Discord OAuth, etc. (plain OAuth 2.0, not OIDC).** No `iss`, no JWKS, opaque
  access tokens. Use them as a federated provider behind a real OIDC IdP, not directly.

## Issuer-consistency gotcha (read this once)

`spring.security.oauth2.resourceserver.jwt.issuer-uri` is used for **two things**:

1. Discovery — the backend hits `{issuer-uri}/.well-known/openid-configuration` at boot.
2. Expected `iss` claim — Spring validates that every incoming token has `iss == issuer-uri`,
   exact-string match including scheme, host, port and trailing slash.

Mismatch shows up as a 401 on **every** request, with `WWW-Authenticate: Bearer error="invalid_token", error_description="The iss claim is not valid"` in the response. The only fix is "make the issuer URL the same everywhere".

In dev this matters because `mock-oauth2-server` derives `iss` from the `Host` header of the
request that minted the token. To keep one hostname in play we use a `/etc/hosts` alias:

```
127.0.0.1 oidc
```

Then everyone — backend container, host JVM, `make token`, browser — addresses the OIDC
server as `oidc:8081`. Backend's discovered issuer (`http://oidc:8081/default`) matches the
`iss` stamped by mock-oauth2-server. `make hosts-check` verifies the alias is present.

In prod this gotcha doesn't apply because the issuer is a real public URL (e.g. `https://
auth.boosting.domain`) which everyone reaches the same way. But the same rule still binds:
whatever value the IdP advertises in its discovery document must match `OIDC_ISSUER_URI`
exactly.

## Backend env vars summary (recap)

The full list lives in `architecture/VAULT_INTEGRATION.md`. The OIDC-relevant subset:

| Env var            | Required in prod       | Meaning                                                          |
|--------------------|------------------------|------------------------------------------------------------------|
| `OIDC_ISSUER_URI`  | yes                    | Issuer URL of the IdP. Must match the `iss` claim byte-for-byte. |
| `OIDC_AUDIENCE`    | yes                    | Expected `aud` value on tokens for this API.                     |
| `APP_ADMIN_EMAILS` | no (empty = no admins) | Comma-separated email allowlist for `ROLE_ADMIN`. Lower-cased.   |
