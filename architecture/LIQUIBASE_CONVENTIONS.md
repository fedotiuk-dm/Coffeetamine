# Liquibase Conventions

> Why this exists: prevent two-month-old habits ("just drop another file into `1.0.0/`") from
> leaking schema across version boundaries. The folder structure below mirrors semver so a
> future developer can read changesets in the order they hit production.

## Layout

```
<module>/src/main/resources/db/changelog/
├── <module>-module-changelog.yaml          # entry point — includes change groups in version order
└── changes/
    ├── 1.0.0/                              # initial MVP schema
    │   └── 1.0.0-<descriptor>.yaml
    ├── 1.1.0/                              # minor feature additions (V1.x)
    │   └── 1.1.0-<descriptor>.yaml
    └── 2.0.0/                              # V2 breaking changes
        └── 2.0.0-<descriptor>.yaml
```

The root master `main/src/main/resources/db/changelog/coffeetamine-changelog.yaml` includes each
module's `<module>-module-changelog.yaml` once — never reach down into a specific changeset file
from the master.

## Versioning rules

| Version bump        | When                                                                                              | Examples                                                              |
|---------------------|---------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------|
| **Patch** (`1.0.x`) | **Never** — Liquibase changesets are append-only. Use a new file under the current minor instead. | n/a                                                                   |
| **Minor** (`1.x.0`) | New feature, additive schema change                                                               | Add `coffee_spots` table, add `mood_color` column to `user_presences` |
| **Major** (`x.0.0`) | Breaking change, destructive migration                                                            | Rename column visible to clients, drop a table referenced elsewhere   |

Bump the minor whenever the **product version** ships — even if your changeset adds only one
nullable column. The folder is your record of what landed in which release.

## Changeset rules

1. **One logical change per changeset.** Splitting `createTable` + `createIndex` into two
   changesets means a failed index can be re-run without re-creating the table.
2. **`id` follows the pattern `<module>-<version>-<verb-noun>`.** Example: `ping-1.0.0-create-pings`.
   This makes the `DATABASECHANGELOG` table self-explanatory.
3. **`preConditions` with `onFail: MARK_RAN`** on every changeset that might be applied to a
   half-migrated database (DB resets in dev are common). Use `not: tableExists` for create,
   `columnExists` for column drops, etc.
4. **Author** matches the GitHub handle — `fedotiuk-dm`, etc. Easy `git blame` from psql.
5. **`comment`** is a one-paragraph explanation of the *why*, not the *what* — the YAML body
   already shows the what.
6. **Never mutate an applied changeset.** If a checksum mismatch hits prod, Liquibase refuses to
   start. Add a new changeset that corrects the schema instead.

## Adding a new changeset

```bash
# 1. Create the file
touch <module>/src/main/resources/db/changelog/changes/1.1.0/1.1.0-add-coffee-spot-fk.yaml

# 2. Wire it into <module>-module-changelog.yaml — bottom of the includes list, version-grouped
#    Example:
#      # 1.1.0 — coffee POI layer
#      - include:
#          file: changes/1.1.0/1.1.0-add-coffee-spot-fk.yaml
#          relativeToChangelogFile: true

# 3. Validate before committing
make migrate-validate

# 4. Apply to local DB to smoke-test
make migrate
```

## Cross-module FKs

Coffeetamine deliberately **does not** use DB-level foreign keys across module boundaries (see
CLAUDE.md). User-owned rows key on the internal `app_user_id` UUID resolved from OIDC
`(issuer, subject)` mappings; there is no local `users` table to FK against. The implication for
Liquibase: include order in the master changelog rarely matters, because no module's table
references another module's table.

The one exception today is **interests**: `user_interest_selections.interest_id → interest_tags(id)` —
this is *within* the interests module, allowed and useful.

## Rollback

We do **not** maintain rollback blocks for MVP. Reasons:

- Schema-only changes can be reverted manually; forward-only migrations are simpler to reason
  about.
- Data migrations needing rollback should be split: do an additive change, deploy, run a separate
  backfill, then deploy the removal. Each step is independently reversible.
- For local dev resets, `make reset` wipes the volume entirely — no rollback needed.

Revisit when V2 introduces data migrations on populated production tables.
