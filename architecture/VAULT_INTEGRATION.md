# Secrets Management — Vault + External Secrets Operator

> Backend reads secrets **as plain environment variables only**. It does not talk to Vault.
> Operators stage secrets in Vault, External Secrets Operator (ESO) syncs them into a
> Kubernetes Secret, and the pod consumes that Secret as env vars. No Spring Cloud Vault,
> no AppRole credentials on the pod, no Vault-side retries baked into Java code.

## Why this shape

The backend used to ship with `spring-cloud-starter-vault-config` so it could read Vault
directly. That was removed deliberately:

- **Operational ownership stays in one place.** Vault policies, lease rotation, ESO sync
  intervals and audit are all in DevOps tooling. The backend image has no Vault SDK,
  no `VAULT_ROLE_ID`, no `SPRING_CLOUD_VAULT_*` env var to misconfigure.
- **Smaller blast radius.** A Vault outage no longer prevents pods from booting if the
  K8s Secret was already synced. Rotation is handled by ESO, not by Spring lifecycle.
- **One source of truth per env var.** No question "does the value come from env or from
  Vault?" — it always comes from env, and the K8s Secret behind that env is the only
  rotation surface.

## Required env vars in prod

The `prod` Spring profile has no defaults for these — a missing value fails the placeholder
resolver at startup (intended fail-fast). Provide every one through the Kubernetes Secret
that ESO maintains.

| Env var                    | Notes                                                                   |
|----------------------------|-------------------------------------------------------------------------|
| `SPRING_PROFILES_ACTIVE`   | Set to `prod`. Activates the prod sub-document in `application.yml`.    |
| `DB_URL`                   | `jdbc:postgresql://<host>:5432/coffeetamine`                            |
| `DB_USERNAME`              |                                                                         |
| `DB_PASSWORD`              |                                                                         |
| `OIDC_ISSUER_URI`          | Issuer URL of the IdP (ZITADEL/Keycloak). Must match the `iss` claim.   |
| `OIDC_AUDIENCE`            | Audience this API expects on incoming tokens (e.g. `coffeetamine-api`). |
| `APP_ADMIN_EMAILS`         | Comma-separated email allowlist for `ROLE_ADMIN`. Empty = no admins.    |

Optional / non-secret tunables (env-only, no Vault needed):

| Env var                                    | Default | Purpose                                    |
|--------------------------------------------|---------|--------------------------------------------|
| `SERVER_PORT`                              | `8090`  | Backend HTTP port.                         |
| `TRACING_SAMPLING`                         | `1.0`   | Lower in prod once volume is real.         |
| `COFFEETAMINE_DISCOVERY_DEFAULT_RADIUS`    | `1000`  | Discovery defaults; see `application.yml`. |
| `COFFEETAMINE_DISCOVERY_MAX_RADIUS`        | `5000`  |                                            |
| `COFFEETAMINE_DISCOVERY_DEFAULT_THRESHOLD` | `30`    |                                            |
| `COFFEETAMINE_DISCOVERY_MIN_THRESHOLD`     | `10`    |                                            |
| `COFFEETAMINE_PRESENCE_JITTER_METERS`      | `80`    |                                            |

## Vault KV layout (suggested)

KV v2 engine at `secret/`. Names match the env vars 1:1 so the ESO `ExternalSecret`
mapping is trivial.

```
secret/coffeetamine/prod
├── DB_URL
├── DB_USERNAME
├── DB_PASSWORD
├── OIDC_ISSUER_URI
├── OIDC_AUDIENCE
└── APP_ADMIN_EMAILS
```

DevOps owns this layout. The backend does not care which path ESO reads from — only that
the resulting Kubernetes Secret carries the env-var keys listed above.

## Reference ExternalSecret manifest

```yaml
apiVersion: external-secrets.io/v1beta1
kind: ExternalSecret
metadata:
  name: coffeetamine-backend
  namespace: coffeetamine
spec:
  refreshInterval: 5m
  secretStoreRef:
    name: vault-backend
    kind: ClusterSecretStore
  target:
    name: coffeetamine-backend          # the Kubernetes Secret the pod mounts
    creationPolicy: Owner
  dataFrom:
    - extract:
        key: secret/coffeetamine/prod   # pulls every KV entry under this path
```

Then in the Deployment:

```yaml
envFrom:
  - secretRef:
      name: coffeetamine-backend
```

Rolling the Secret picks up new values on the next pod restart. Pair with
[Reloader](https://github.com/stakater/Reloader) (or equivalent) if you need automatic
rollout when ESO updates the Secret.

## Acceptance checklist

- [ ] Vault KV v2 enabled at `secret/`; `secret/coffeetamine/prod` populated.
- [ ] ESO running in the cluster, `ClusterSecretStore` bound to Vault.
- [ ] `ExternalSecret` resource above (or equivalent) created in the app namespace.
- [ ] Backend Deployment uses `envFrom: secretRef: coffeetamine-backend`.
- [ ] Pod boots with `SPRING_PROFILES_ACTIVE=prod`; logs show no placeholder errors.
- [ ] Reloader (or rollout step in CI) restarts pods when ESO rewrites the Secret.

## What this scaffold does NOT do

- No Vault container in `docker-compose.dev.yml`. Local dev uses the env-var defaults from
  `application.yml` and `docker-compose.dev.yml`.
- No `init-vault.sh` or KV seeding scripts. DevOps owns secret material.
- No backend-side Vault SDK. Removing the dependency was deliberate — see above.
- No dynamic database credentials. If short-lived DB creds become a requirement, add an
  ESO `ExternalSecret` with a `vault.kv` generator pointing at Vault's
  `database/creds/<role>` endpoint and let ESO renew the lease into the Kubernetes Secret.
  The backend still reads plain env-vars.
