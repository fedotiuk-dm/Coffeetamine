# Production image for Coffeetamine backend.
# Multi-stage: Liberica JDK 25 (Alpaquita musl) compiles the multi-module project, then a
# slim Liberica JRE 25 image runs the repackaged executable jar produced by `main`.
#
# Liberica is BellSoft's TCK-verified OpenJDK distribution on Alpaquita Linux (musl + jemalloc,
# JVM-tuned kernel). Matches the runtime used in the host IDE — no JDK-vendor drift between
# laptop and image. The project does not ship `mvnw`, so Maven is installed from the official
# Apache binary (version pinned).
#
# Build:  docker build -t coffeetamine-backend:local .
# Run:    docker run --rm -p 8090:8090 \
#           -e OIDC_ISSUER_URI=http://host.docker.internal:8081/default \
#           -e OIDC_AUDIENCE=coffeetamine-api \
#           -e DB_URL=jdbc:postgresql://host.docker.internal:5432/coffeetamine \
#           coffeetamine-backend:local

# =============================================================================
# BUILD STAGE
# =============================================================================
FROM bellsoft/liberica-runtime-container:jdk-25-musl AS build

ARG MVN_VER=3.9.9
ARG MVN_URL=https://archive.apache.org/dist/maven/maven-3
ARG MVN_TGZ=apache-maven-${MVN_VER}-bin.tar.gz
ARG APP_VERSION=0.1.0-SNAPSHOT

ENV MAVEN_HOME=/opt/maven \
    PATH=/opt/maven/bin:$PATH \
    MAVEN_OPTS="-Xmx1024m -Dorg.slf4j.simpleLogger.defaultLogLevel=WARN"

RUN apk add --no-cache bash curl tar \
    && cd /tmp \
    && curl -fsSL -o maven.tar.gz        "${MVN_URL}/${MVN_VER}/binaries/${MVN_TGZ}" \
    && curl -fsSL -o maven.tar.gz.sha512 "${MVN_URL}/${MVN_VER}/binaries/${MVN_TGZ}.sha512" \
    && echo "$(cat maven.tar.gz.sha512)  maven.tar.gz" | sha512sum -c - \
    && mkdir -p /opt/maven \
    && tar -xzf maven.tar.gz -C /opt/maven --strip-components=1 \
    && rm maven.tar.gz maven.tar.gz.sha512 \
    && mvn -v

WORKDIR /workspace

# 1. Parent POM first — best Docker layer cache hit when only source changes.
COPY pom.xml ./

# 2. Every module POM. Keep this list in sync with <modules> in the parent pom.
COPY common/pom.xml         ./common/
COPY user/pom.xml           ./user/
COPY interests/pom.xml      ./interests/
COPY presence/pom.xml       ./presence/
COPY mood-board/pom.xml     ./mood-board/
COPY discovery/pom.xml      ./discovery/
COPY ping/pom.xml           ./ping/
COPY notification/pom.xml   ./notification/
COPY main/pom.xml           ./main/

# 3. Align Maven artifact version with the image tag, then warm the local Maven repo.
RUN mvn -B -q org.codehaus.mojo:versions-maven-plugin:2.17.1:set \
      -DnewVersion="${APP_VERSION}" \
      -DgenerateBackupPoms=false \
    && mvn -B -q -Pdev-fast dependency:go-offline

# 4. Source code — same module list.
COPY common/src        ./common/src
COPY user/src          ./user/src
COPY interests/src     ./interests/src
COPY presence/src      ./presence/src
COPY mood-board/src    ./mood-board/src
COPY discovery/src     ./discovery/src
COPY ping/src          ./ping/src
COPY notification/src  ./notification/src
COPY main/src          ./main/src
COPY openapi/          ./openapi/

# 5. Package. spring-boot:repackage on `main` produces the executable jar.
RUN mvn -B -q -Pdev-fast -DskipTests -T 1C clean package

# =============================================================================
# RUNTIME STAGE
# =============================================================================
FROM bellsoft/liberica-runtime-container:jre-25-musl AS runtime

ARG APP_VERSION=0.1.0-SNAPSHOT

RUN apk add --no-cache curl tini \
    && addgroup -S -g 1000 app \
    && adduser  -S -u 1000 -G app -s /sbin/nologin app

WORKDIR /app

COPY --from=build --chown=app:app /workspace/main/target/coffeetamine-main-*.jar /app/app.jar

USER app

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseG1GC -Dspring.jmx.enabled=false"
ENV APP_VERSION=${APP_VERSION}
ENV SERVER_PORT=8090
ENV SPRING_PROFILES_ACTIVE=prod

LABEL org.opencontainers.image.version="${APP_VERSION}"

EXPOSE 8090 9090

HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
  CMD curl -fsS "http://localhost:9090/actuator/health/liveness" || exit 1

ENTRYPOINT ["/sbin/tini", "--"]
CMD ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
