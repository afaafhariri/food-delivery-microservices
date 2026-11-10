# syntax=docker/dockerfile:1
#
# One Dockerfile for every JVM service:
#   docker build -f docker/jvm.Dockerfile --build-arg SERVICE=order-service .
#
# Stages:  build (Maven) -> jre (jlink) -> runtime (alpine + jre) -> app (layers + AOT cache)

ARG BUILD_IMAGE=maven:3.9-eclipse-temurin-25-noble
ARG JDK_IMAGE=eclipse-temurin:25-jdk-alpine
ARG RUNTIME_IMAGE=alpine:3.24

# ---------------------------------------------------------------------------
# 1. Build only the requested module and the reactor modules it depends on.
#    ~/.m2 is a BuildKit cache mount shared by all service builds.
# ---------------------------------------------------------------------------
FROM ${BUILD_IMAGE} AS build
ARG SERVICE
WORKDIR /src
COPY . .
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    test -n "${SERVICE}" && \
    mvn -B -q -ntp -pl "${SERVICE}" -am package -DskipTests && \
    java -Djarmode=tools -jar "${SERVICE}/target/${SERVICE}-"*.jar \
        extract --layers --application-filename app.jar --destination /layers

# ---------------------------------------------------------------------------
# 2. Custom JRE (~65 MB vs ~200 MB for a full JDK). The module set is the
#    jdeps union over all services plus modules that are only reached via
#    reflection / ServiceLoader. Same for every service -> one shared layer.
# ---------------------------------------------------------------------------
FROM ${JDK_IMAGE} AS jre
RUN apk add --no-cache binutils && \
    jlink \
      --add-modules java.base,java.compiler,java.desktop,java.instrument,java.net.http,java.prefs,java.rmi,java.scripting,java.security.jgss,java.security.sasl,java.sql.rowset,jdk.jfr,jdk.management,jdk.unsupported,jdk.naming.dns,jdk.zipfs \
      --strip-debug --no-man-pages --no-header-files \
      --output /opt/jre && \
    /opt/jre/bin/java -XX:+UseSerialGC -XX:+UseCompactObjectHeaders -Xshare:dump -version
# Default CDS archive for JDK classes (~13 MB) -> faster startup with no per-app
# training. It is dumped with the runtime's GC / object-header flags; an archive
# built with other flags would be silently ignored. lib/modules stays
# uncompressed on purpose: zip saves ~30 MB on disk, but every class load pays.

# ---------------------------------------------------------------------------
# 3. Runtime base: musl + JRE + unprivileged user. busybox wget is kept for
#    container health checks.
# ---------------------------------------------------------------------------
FROM ${RUNTIME_IMAGE} AS runtime
ENV JAVA_HOME=/opt/jre \
    PATH=/opt/jre/bin:$PATH \
    LANG=C.UTF-8 \
    LIQUIBASE_ANALYTICS_ENABLED=false
# Liquibase analytics phone home on every start: ~1.2 s of startup and an
# outbound data leak. Off.
COPY --from=jre /opt/jre /opt/jre
RUN addgroup -S -g 10001 app && \
    adduser -S -D -H -u 10001 -G app -s /sbin/nologin app

# ---------------------------------------------------------------------------
# 4. Application. Layers are ordered from least to most frequently changed,
#    so a code change only ships the small application layer.
#
#    AOT cache (default on): a training run starts the Spring context (beans
#    created, nothing started) and records a JDK 25 AOT cache: ~80% of all
#    classes pre-parsed and pre-linked, plus method profiles.
#    Measured (order-service, 384 MB limit, 2 vCPU):
#        startup  4.6-5.2 s -> 2.2-3.0 s      RSS  ~290 MB -> ~257 MB
#    Cost: ~140-170 MB per image (the app.aot layer). --build-arg AOT_CACHE=false
#    trades that back for slower starts.
#    No DB or Kafka is needed for training: Liquibase and Hibernate's JDBC
#    metadata lookup are disabled for that run only.
# ---------------------------------------------------------------------------
FROM runtime AS app
ARG AOT_CACHE=true
WORKDIR /app
COPY --from=build /layers/dependencies/ ./
COPY --from=build /layers/spring-boot-loader/ ./
COPY --from=build /layers/snapshot-dependencies/ ./
COPY --from=build /layers/application/ ./
COPY docker/jvm.options ./
RUN if [ "${AOT_CACHE}" = "true" ]; then \
      java @jvm.options \
        -XX:AOTCacheOutput=app.aot \
        -Xlog:aot=error \
        -Dspring.context.exit=onRefresh \
        -Dspring.main.banner-mode=off \
        -Dspring.liquibase.enabled=false \
        -Dspring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false \
        -Dspring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect \
        -jar app.jar && \
      test -s app.aot && \
      echo "-XX:AOTCache=/app/app.aot" >> jvm.options; \
    fi && \
    rm -rf /tmp/* app.aot.config

USER app:app
ENTRYPOINT ["java", "@/app/jvm.options", "-jar", "/app/app.jar"]
