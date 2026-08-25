# syntax=docker/dockerfile:1.7@sha256:a57df69d0ea827fb7266491f2813635de6f17269be881f696fbfdf2d83dda33e

ARG SOURCE_DATE_EPOCH=946684800

# Builder images are pinned to immutable multi-platform manifests. Node and the JDK
# never cross into the runtime stage.
FROM node:22.22.0-alpine3.23@sha256:e4bf2a82ad0a4037d28035ae71529873c069b13eb0455466ae0bc13363826e34 AS frontend-build

WORKDIR /workspace/frontend
COPY docs/openapi.json /workspace/docs/openapi.json
COPY frontend/package.json frontend/package-lock.json frontend/openapi-ts.config.ts ./
RUN --mount=type=cache,target=/root/.npm \
    npm ci --ignore-scripts --no-audit --no-fund

COPY frontend/index.html frontend/postcss.config.js frontend/tailwind.config.js ./
COPY frontend/tsconfig.json frontend/tsconfig.app.json frontend/tsconfig.node.json ./
COPY frontend/vite.config.ts ./
COPY frontend/public/ ./public/
COPY frontend/src/ ./src/
RUN npm run generate-api && npm run build

FROM eclipse-temurin:21.0.10_7-jdk-alpine-3.23@sha256:bcc7ec7e8fef937ba9f01ee5f810361d722c6b5dbe19ac188ab7b25c1a4dd2c9 AS backend-build

ARG SOURCE_DATE_EPOCH
WORKDIR /workspace/backend
COPY backend/gradlew backend/build.gradle backend/settings.gradle ./
COPY backend/gradle/ ./gradle/
COPY backend/src/main/resources/ ./src/main/resources/
RUN --mount=type=cache,target=/root/.gradle \
    chmod +x gradlew && \
    ./gradlew dependencies --configuration runtimeClasspath --no-daemon

COPY backend/src/main/java/ ./src/main/java/
COPY --from=frontend-build /workspace/frontend/dist/ ./src/main/resources/static/
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew bootJar --no-daemon -PskipFrontend && \
    artifact="$(find build/libs -maxdepth 1 -type f -name '*.jar' ! -name '*-plain.jar' ! -name '*-test-fixtures.jar' -print -quit)" && \
    test -n "$artifact" && \
    jar tf "$artifact" | grep -q '^BOOT-INF/classes/static/index.html$' && \
    mkdir -p /workspace/runtime/app && \
    cp "$artifact" /workspace/runtime/app/junto.jar && \
    touch -d "@${SOURCE_DATE_EPOCH}" \
      /workspace/runtime/app/junto.jar \
      /workspace/runtime/app \
      /workspace/runtime

# Distroless supplies only a Java 21 runtime and OS runtime data: no shell,
# package manager, Node/npm, or JDK tooling.
FROM gcr.io/distroless/java21-debian12:nonroot@sha256:7e37784d94dccbf5ccb195c73b295f5ad00cd266512dfbac12eb9c3c28f8077d

LABEL org.opencontainers.image.title="Junto" \
      org.opencontainers.image.description="Junto Vue frontend and Spring Boot production application" \
      org.opencontainers.image.source="https://github.com/mrspacerobot/junto"

# Copy a complete, source-epoch-normalized runtime tree so the /app directory
# metadata is deterministic as well as the JAR itself.
COPY --from=backend-build --chown=65532:65532 /workspace/runtime/ /
WORKDIR /app

ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -Djava.io.tmpdir=/tmp"

USER 65532:65532
EXPOSE 8080
STOPSIGNAL SIGTERM
ENTRYPOINT ["java", "-jar", "/app/junto.jar"]
