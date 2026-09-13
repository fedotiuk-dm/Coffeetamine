# ╔══════════════════════════════════════════════════════════════════════════════╗
# ║  Coffeetamine — Backend                                                      ║
# ║  Usage: make [target]                                                        ║
# ╚══════════════════════════════════════════════════════════════════════════════╝

.DEFAULT_GOAL := help

# ── Variables ─────────────────────────────────────────────────────────────────
MVN        := mvn
COMPOSE    := docker compose -f docker-compose.dev.yml
DEV_IMAGE  := coffeetamine-backend:dev

# Run Maven INSIDE the Liberica dev image — no host JDK/Maven required.
#
# Mount layout:
#   - $(CURDIR) → /app                       : source tree (artefacts land back in target/)
#   - coffeetamine_maven_cache → /home/appuser/.m2 : warm Maven cache (same volume
#     docker-compose uses, project-prefixed name)
#
# `--user` mirrors the host UID/GID so files written under target/ stay owned by
# the host user. `--entrypoint mvn` bypasses docker-entrypoint.dev.sh (which
# launches spring-boot:run) so we can pass arbitrary Maven goals.
DOCKER_MVN := docker run --rm \
              -v "$(CURDIR)":/app \
              -v coffeetamine_maven_cache:/home/appuser/.m2 \
              -w /app \
              --user "$(shell id -u):$(shell id -g)" \
              --entrypoint mvn \
              $(DEV_IMAGE)

.PHONY: help info install build build-fast build-host clean dev test test-int \
        format check up up-infra up-db down down-keep logs status db-shell \
        reset image migrate migrate-status migrate-validate hosts-check \
        token token-admin _dev_image _show_info update-all-major

# ╔══════════════════════════════════════════════════════════════════════════════╗
# ║  Help & Info                                                                 ║
# ╚══════════════════════════════════════════════════════════════════════════════╝

help: ## Show this help
	@echo ''
	@echo '  Coffeetamine — backend targets'
	@echo ''
	@awk 'BEGIN {FS = ":.*## "} \
		/^[a-zA-Z_-]+:.*## / { printf "  \033[36m%-18s\033[0m %s\n", $$1, $$2 }' $(MAKEFILE_LIST)
	@$(MAKE) --no-print-directory _show_info

info: ## Show URLs, ports, dev credentials
	@$(MAKE) --no-print-directory _show_info

# ╔══════════════════════════════════════════════════════════════════════════════╗
# ║  Build & Run                                                                 ║
# ╚══════════════════════════════════════════════════════════════════════════════╝

# Docker-driven build — the default. First invocation builds the dev image (one-shot),
# subsequent runs reuse the warm Maven cache in the named volume.
build: _dev_image ## Build inside the Liberica dev container (skip tests)
	@$(DOCKER_MVN) -B clean package -DskipTests -T 1C

build-fast: _dev_image ## Build inside container, skip tests + Spotless + Checkstyle (-Pdev-fast)
	@$(DOCKER_MVN) -B clean package -DskipTests -T 1C -Pdev-fast

build-host: ## Build on the HOST JDK/Maven (no Docker)
	@$(MVN) clean package -DskipTests

install: ## Install dependencies on host (skip tests) — populates host ~/.m2 for the IDE
	@$(MVN) clean install -DskipTests

clean: ## mvn clean (host)
	@$(MVN) clean

dev: ## Run the app from main/ on host JDK (dev profile)
	@$(MVN) -pl main -am spring-boot:run -Dspring-boot.run.profiles=dev

# ╔══════════════════════════════════════════════════════════════════════════════╗
# ║  Quality                                                                     ║
# ╚══════════════════════════════════════════════════════════════════════════════╝

test: ## Unit tests (host)
	@$(MVN) test

test-int: ## Integration tests — Failsafe, *IT.java (host)
	@$(MVN) verify

format: ## Apply Spotless (Google Java Format + import order + POM sort)
	@$(MVN) spotless:apply

check: ## Checkstyle (warning-only, never fails build)
	@$(MVN) checkstyle:check

# ╔══════════════════════════════════════════════════════════════════════════════╗
# ║  Database Migrations (Liquibase)                                             ║
# ╚══════════════════════════════════════════════════════════════════════════════╝

migrate: ## Apply pending migrations against the local DB
	@$(MVN) -pl main liquibase:update

migrate-status: ## Show unapplied changesets
	@$(MVN) -pl main liquibase:status

migrate-validate: ## Validate the changelog (catches checksum mismatches, broken includes)
	@$(MVN) -pl main liquibase:validate

# ╔══════════════════════════════════════════════════════════════════════════════╗
# ║  Docker Services (local dev stack)                                           ║
# ╚══════════════════════════════════════════════════════════════════════════════╝

up: ## Bring up the FULL dev stack (Postgres + mock OIDC + backend hot reload)
	@$(COMPOSE) up -d --build
	@$(MAKE) --no-print-directory _show_info

up-infra: ## Bring up only infra (Postgres + mock OIDC) — for running the app from the IDE
	@$(COMPOSE) up -d postgres oidc

up-db: ## Bring up only Postgres (app DB), no OIDC IdP
	@$(COMPOSE) up -d postgres

down: ## Stop containers, keep volumes
	@$(COMPOSE) down

down-keep: ## Alias for `down`
	@$(COMPOSE) down

reset: ## Stop containers AND wipe volumes (full reset incl. .m2 cache)
	@$(COMPOSE) down -v

logs: ## Tail logs from all services
	@$(COMPOSE) logs -f

status: ## Show container status
	@$(COMPOSE) ps

db-shell: ## psql shell into the app database
	@docker exec -it coffeetamine-postgres psql -U coffeetamine -d coffeetamine

# ╔══════════════════════════════════════════════════════════════════════════════╗
# ║  OIDC tokens / hosts check                                                   ║
# ╚══════════════════════════════════════════════════════════════════════════════╝

hosts-check: ## Verify /etc/hosts has `127.0.0.1 oidc` (required so JWT iss matches OIDC_ISSUER_URI)
	@grep -qE '^[^#]*127\.0\.0\.1[[:space:]]+(.*[[:space:]])?oidc([[:space:]]|$$)' /etc/hosts || ( \
		echo ''; \
		echo '  /etc/hosts is missing the `oidc` alias.'; \
		echo ''; \
		echo '  mock-oauth2-server derives the JWT `iss` claim from the Host header on the'; \
		echo '  request that minted the token. The backend — both from the host JVM (`make dev`)'; \
		echo '  and from the docker network (`make up`) — must address it under the same name'; \
		echo '  `oidc:8081`, so that the discovered issuer (which becomes the expected `iss`)'; \
		echo '  matches the one stamped into tokens.'; \
		echo ''; \
		echo '  Run once (asks for sudo):'; \
		echo '    echo "127.0.0.1 oidc" | sudo tee -a /etc/hosts'; \
		echo ''; \
		exit 1 \
	)
	@echo '  /etc/hosts has the oidc alias — issuer consistency OK.'

token: ## Mint a USER JWT from mock-oauth2-server (sub=dev) — for curl/Postman
	@curl -sS --resolve oidc:8081:127.0.0.1 -X POST 'http://oidc:8081/default/token' \
		-d 'client_id=coffeetamine-mobile' \
		-d 'grant_type=password' \
		-d 'username=dev' \
		-d 'password=any' \
		-d 'scope=openid email profile' \
		| python3 -c 'import json,sys; print(json.load(sys.stdin)["access_token"])'

token-admin: ## Mint an ADMIN JWT (email matches APP_ADMIN_EMAILS, email_verified=true)
	@curl -sS --resolve oidc:8081:127.0.0.1 -X POST 'http://oidc:8081/default/token' \
		-d 'client_id=coffeetamine-mobile' \
		-d 'grant_type=password' \
		-d 'username=admin@coffeetamine.local' \
		-d 'password=any' \
		-d 'scope=openid email profile' \
		| python3 -c 'import json,sys; print(json.load(sys.stdin)["access_token"])'

# ╔══════════════════════════════════════════════════════════════════════════════╗
# ║  Production image (DevOps)                                                   ║
# ╚══════════════════════════════════════════════════════════════════════════════╝

image: ## Build the production Docker image (Liberica multi-stage)
	@docker build -t coffeetamine-backend:local .

# ╔══════════════════════════════════════════════════════════════════════════════╗
# ║  Private Helpers                                                             ║
# ╚══════════════════════════════════════════════════════════════════════════════╝

# Build the dev image once if missing. `make build` / `make build-fast` depend on
# this so a fresh clone Just Works without requiring `make up` first.
_dev_image:
	@docker image inspect $(DEV_IMAGE) >/dev/null 2>&1 || { \
		echo 'Building dev image (one-shot — cached on subsequent runs)...'; \
		docker build -f Dockerfile.dev -t $(DEV_IMAGE) . ; \
	}

update-all-major: ## Update everything including MAJOR versions (dangerous!)
	@echo 'WARNING: This will update to MAJOR versions — may break things!'
	@read -p "Continue? (y/N): " confirm && [ "$$confirm" = "y" ] || exit 1
	@$(MVN) versions:use-latest-versions -DallowMajorUpdates=true
	@$(MVN) versions:update-properties -DallowMajorUpdates=true
	@echo 'Major updates completed. Test thoroughly!'

_show_info:
	@echo ''
	@echo '  ┌──────────────────────────┬──────────────────────────────────────────┐'
	@echo '  │  URLs                                                               │'
	@echo '  ├──────────────────────────┼──────────────────────────────────────────┤'
	@echo '  │  Backend API             │  http://localhost:8090                   │'
	@echo '  │  Swagger UI              │  http://localhost:8090/swagger-ui.html   │'
	@echo '  │  Actuator                │  http://localhost:8090/actuator          │'
	@echo '  │  OIDC IdP (mock)         │  http://oidc:8081/default                │'
	@echo '  │  JDWP debug              │  localhost:5005                          │'
	@echo '  │  DevTools LiveReload     │  localhost:35729                         │'
	@echo '  ├──────────────────────────┴──────────────────────────────────────────┤'
	@echo '  │  Dev Credentials                                                    │'
	@echo '  ├──────────────────────────┬──────────────────────────────────────────┤'
	@echo '  │  PostgreSQL              │  coffeetamine / coffeetamine             │'
	@echo '  │                          │  localhost:5432 / coffeetamine           │'
	@echo '  │  Admin email allowlist   │  admin@coffeetamine.local                │'
	@echo '  └──────────────────────────┴──────────────────────────────────────────┘'
	@echo ''
	@echo '  Build:      make build         (inside Liberica dev container)'
	@echo '              make build-fast    (-Pdev-fast, also containerised)'
	@echo '              make build-host    (host JDK/Maven, no Docker)'
	@echo '  Hot reload: recompile in IntelliJ (Ctrl+F9) to trigger DevTools restart.'
	@echo '  Mint JWT:   make token         (or make token-admin)'
	@echo ''
