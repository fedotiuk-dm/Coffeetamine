#!/bin/bash
set -e

# Dev entrypoint — see Dockerfile.dev for the surrounding hot-reload pattern.
# Runs an incremental `mvn install` (only changed modules get rebuilt thanks to the
# named-volume .m2 cache + build cache) and hands off to `spring-boot:run` with
# DevTools + JDWP attached.

echo "=========================================="
echo "Coffeetamine — backend (dev hot reload)"
echo "=========================================="

echo "[1/2] Maven install (skip tests, dev-fast profile)..."
mvn install -DskipTests -T 1C -Pdev-fast -q

echo "[2/2] Starting Spring Boot..."
echo "      Profile      : ${SPRING_PROFILES_ACTIVE:-dev}"
echo "      Debug (JDWP) : 5005"
echo "      DevTools     : enabled (LiveReload on 35729)"
echo "=========================================="

exec mvn -pl main spring-boot:run \
  -Dspring-boot.run.fork=false \
  -Dspring-boot.run.profiles="${SPRING_PROFILES_ACTIVE:-dev}" \
  -Dspring-boot.run.jvmArguments="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005 -Dspring.devtools.restart.enabled=true -Dspring.devtools.livereload.enabled=true"
