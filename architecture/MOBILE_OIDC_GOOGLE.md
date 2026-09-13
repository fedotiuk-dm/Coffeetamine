# Mobile Auth — OIDC (with Google as federated IdP)

How Coffeetamine's mobile clients log in: **OIDC Authorization Code + PKCE against an
OIDC provider.** When the user authenticates with Google, Google is configured as a
federated IdP *inside* the OIDC provider — the mobile app never talks to Google
directly. The backend is provider-agnostic; swap ZITADEL for Keycloak (or any
spec-compliant OIDC issuer) by changing two env vars.

## Why this shape

- **One auth surface for the backend.** Backend is a plain OAuth2 Resource Server that
  validates JWTs via JWKS and checks the `aud` claim. It does not care which IdP
  authenticated the user, or even which OIDC provider issued the token, beyond matching
  the configured `OIDC_ISSUER_URI` + `OIDC_AUDIENCE`. Future end-user IdPs (Apple,
  email/password, SSO) plug into the OIDC provider without touching the backend.
- **Secrets are unsafe in mobile apps.** A confidential client secret embedded in an APK
  / IPA can be extracted. The mobile client must be a **public client**, and PKCE
  replaces the secret as the code-exchange security factor.
- **Google blocks OAuth in WebViews.** Auth must run in a system browser tab
  (Chrome Custom Tabs / `ASWebAuthenticationSession`). The AppAuth libraries enforce this.

## Backend contract

The backend needs three env vars and nothing else provider-specific:

| Env var             | Purpose                                                                           |
|---------------------|-----------------------------------------------------------------------------------|
| `OIDC_ISSUER_URI`   | Discovery endpoint — JWKS resolved at `{issuer}/.well-known/openid-configuration` |
| `OIDC_AUDIENCE`     | Expected `aud` claim; enforced by Spring Security JWT audience validation          |
| `APP_ADMIN_EMAILS`  | Comma-separated email allowlist that earns `ROLE_ADMIN`                           |

`SecurityUtils.getCurrentUserId()` returns the internal `app_user_id` UUID, which
`UserIdentityResolver` mints (and caches) on first sight of a new `(issuer, subject)`
pair. The JWT `sub` is treated as an opaque string. Per-domain profile tables key on
`app_user_id`, never on the raw subject.

Authorities: every authenticated principal gets `ROLE_USER`. `ROLE_ADMIN` is granted
**only** when the JWT `email` claim is in `app.admin-emails` — the IdP's own role claims
are intentionally ignored, so an over-permissive IdP role mapping cannot escalate
privileges in the backend.

## OIDC provider — what to configure

Regardless of which provider you run, you need:

1. An OIDC issuer (the URL fed into `OIDC_ISSUER_URI`).
2. A **public** OIDC client for the mobile app:
   - Client authentication: **OFF** (no secret).
   - Standard Flow / Authorization Code: **ON**.
   - PKCE: **required, S256**.
   - Redirect URIs (one per scheme you support):
     - Custom scheme — `coffeetamine://oauth/callback`
     - Android App Link — `https://coffeetamine.app/oauth/callback`
     - iOS Universal Link — `https://coffeetamine.app/oauth/callback`
   - Post-logout redirect URIs: same list.
3. An audience claim equal to `OIDC_AUDIENCE` baked into access tokens for that client.
4. Google configured as a federated IdP (so users can "sign in with Google" from the
   provider's hosted login screen). Required scopes: `openid profile email`.

## Mobile flow (AppAuth)

Reference libraries: `AppAuth-Android`, `AppAuth-iOS`. Both implement
[RFC 8252](https://datatracker.ietf.org/doc/html/rfc8252) (OAuth 2.0 for Native Apps).

```
┌────────────┐   1. discovery (.well-known/openid-configuration)  ┌───────────────┐
│ Mobile app │ ─────────────────────────────────────────────────▶ │ OIDC provider │
│            │                                                    │               │
│            │ 2. open Chrome Custom Tabs / ASWebAuthSession       │               │
│            │    → /authorize?client_id=coffeetamine-mobile       │               │
│            │      &response_type=code&code_challenge=...         │               │
│            │      &code_challenge_method=S256                    │               │
│            │      &redirect_uri=coffeetamine://oauth/callback    │               │
│            │      &scope=openid profile email                    │               │
│            │      &<idp-hint>=google           ◀── (optional)    │               │
└────────────┘                                                    └────────┬──────┘
                                                                           │
                            3. Provider sees the IdP hint (or shows its    │
                               own picker), redirects the system browser   │
                               to Google sign-in.                          │
                                                                           ▼
                                                                   ┌──────────────┐
                                                                   │    Google    │
                                                                   └──────┬───────┘
                            4. User authenticates with Google. Google      │
                               redirects back to the OIDC provider's       │
                               broker endpoint.                            │
                                                                   ┌───────▼──────┐
                            5. Provider creates/finds user, issues an       │ OIDC│
                               authorization code, redirects to             │ prov│
                               coffeetamine://oauth/callback?code=…         └──┬──┘
                                                                                │
                            6. OS intercepts the redirect, hands the code      │
                               back to the app via AppAuth.                    │
┌────────────┐                                                                  ▼
│ Mobile app │ ◀────────────────────────────────────────────────────────────────
│            │
│            │ 7. POST /token  client_id=coffeetamine-mobile
│            │                 grant_type=authorization_code
│            │                 code=…  code_verifier=…
│            │                 redirect_uri=coffeetamine://oauth/callback
│            │ ───────────────────────────────────────────────▶ OIDC provider
│            │
│            │ ◀────────────── { access_token, id_token, refresh_token }
│            │
│            │ 8. Every API call:
│            │    Authorization: Bearer <access_token>            ─▶ Backend
└────────────┘                                                          │
                                                                        ▼
                                          Backend validates via JWKS + audience,
                                          resolves (iss, sub) → app_user_id UUID.
```

The IdP hint (`kc_idp_hint=google` for Keycloak, `idp_hint=…` for ZITADEL, etc.) is
**optional but recommended** — without it the provider shows its own login screen first
("Sign in with Google" button), which is poor UX in a native app. Name and value depend
on the provider; see provider-specific notes below.

## Token storage on device

- iOS: **Keychain** with `kSecAttrAccessibleAfterFirstUnlock` (so background refresh works
  after device reboot + first unlock).
- Android: **EncryptedSharedPreferences** (Jetpack Security) with `MasterKey` from the
  StrongBox-backed AndroidKeyStore where available.
- Never log tokens. Never write them to plaintext disk.
- **Refresh tokens are long-lived** — treat them like passwords.

## Token refresh

Access tokens are short-lived (5–15 min, provider default). When a request returns 401,
the mobile client POSTs to `/token` with `grant_type=refresh_token` and the stored
refresh token to obtain a new access token. AppAuth automates this — wire it into the
HTTP client interceptor.

## Logout

- Call the provider's end-session endpoint (advertised in `.well-known/openid-configuration`
  as `end_session_endpoint`) with `refresh_token` to invalidate the session server-side.
- Locally: wipe Keychain / EncryptedSharedPreferences entries.
- Optionally redirect the system browser to the end-session URL with `id_token_hint=…`
  to clear the provider's SSO cookie — usually unnecessary for native apps since the SSO
  cookie lives in the browser tab, not the app.

## Production hardening checklist

| Item                                                                                  | Why |
|---------------------------------------------------------------------------------------|-----|
| **App Links / Universal Links** instead of custom URI scheme                          | Custom schemes can be hijacked by a malicious app declaring the same scheme. Verified universal/app links are domain-bound. |
| Enforce PKCE S256 server-side on the client config                                    | Prevents a misconfigured app from omitting PKCE. |
| **Certificate pinning** for the OIDC provider hostname                                | Defends against TLS MITM on hostile networks. |
| Short access token TTL (5–15 min) + reasonable refresh token TTL (e.g. 30 days idle)  | Limits blast radius of a stolen token. |
| Implement back-channel logout if you support multi-device                             | Lets you remotely sign out one device. |
| Capture `nonce` from the auth request and verify it in the `id_token`                 | Replay protection — AppAuth does this for you. |
| Keep `APP_ADMIN_EMAILS` short and version-controlled                                  | The backend ignores IdP role claims; the email allowlist is the only way to grant `ROLE_ADMIN`. |

## Things that look tempting but are wrong

- **"Mobile gets a Google ID token directly, sends it to backend, backend exchanges it
  with the OIDC provider."** Couples the backend to Google specifics, requires Token
  Exchange (preview in most providers), and breaks the "one auth surface" property.
  Don't do this.
- **Embedding a confidential client secret in the app.** Trivially extractable. PKCE +
  public client is the answer.
- **Using a WebView for the auth screen.** Google explicitly blocks this
  (`disallowed_useragent` error). It also bypasses the OS keychain integration that
  legitimate browser tabs use for password autofill.
- **Implementing your own OAuth state machine instead of using AppAuth.** RFC 8252 is
  full of subtle pitfalls (redirect interception, state validation, PKCE, etc.). AppAuth
  has them solved.
- **Granting `ROLE_ADMIN` via an IdP role claim.** The backend ignores them by design —
  managing the privilege boundary in the IdP gives that IdP unilateral admin-grant power
  over your platform.

## Local dev — `mock-oauth2-server`

`docker-compose.dev.yml` runs `ghcr.io/navikt/mock-oauth2-server:2.1.10` at
`http://localhost:8081/default`. No admin UI, no realm import, no per-environment Postgres.
The container's debugger UI at `http://localhost:8081/default/debugger` lets you mint a
token for any subject/email by clicking "Get a token", and `make token` /
`make token-admin` automate this for `curl` / Postman use. Tokens are signed with the
provider's local JWKS and `aud=coffeetamine-api`, matching the backend's configured
audience.

The mock server is **for development only**. It accepts arbitrary subjects and emails
with no authentication — never expose it outside `localhost`.

## Provider-specific operator notes

The setup below is for production / staging. None of it changes the backend.

### Keycloak

- Create a realm. Add a public client (Client authentication OFF, Standard Flow ON,
  Direct Access Grants OFF, PKCE S256 enforced server-side).
- Identity Providers → Add provider → Google → paste Client ID / Secret from Google
  Cloud Console (Web application OAuth client, redirect URI
  `https://kc.example.com/realms/<realm>/broker/google/endpoint`).
- IdP hint param for the mobile authorize URL: `kc_idp_hint=google`.
- Audience: add a hardcoded audience mapper on the client so access tokens carry
  `aud=coffeetamine-api`, per the Keycloak audience-mapper docs.

### ZITADEL

- Create a project, add an application (Type: User Agent / Native; Auth Method: PKCE).
- Add Google as an identity provider under the organization or instance settings
  (Settings → Identity Providers → Google), paste OAuth client credentials.
- ZITADEL emits the `aud` claim from the project's audience configuration — confirm it
  contains `coffeetamine-api` (project general settings or via a custom claim).
- IdP hint param for the mobile authorize URL: consult ZITADEL's current API docs — the
  exact parameter name has changed across versions; the IdP ID is surfaced in the
  ZITADEL admin UI after creating the provider.

### Anything else

As long as the provider:

1. Publishes a valid `.well-known/openid-configuration`,
2. Issues JWT access tokens with `aud=<your OIDC_AUDIENCE>`,
3. Includes a stable `sub` per user and an `email` claim,

…the backend will work unchanged.
