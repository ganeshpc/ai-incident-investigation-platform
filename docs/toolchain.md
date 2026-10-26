# Toolchain

Pinned on 2026-10-26 from the latest stable releases that this environment can build. Release candidates are not used. Spring Boot 4.2.0 was a release candidate, so the backend stays on 4.1.2.

## Observed developer machine

| Tool | Version |
|---|---|
| Java | OpenJDK 21.0.11 Temurin |
| Maven | 3.9.11 installed; the backend wrapper downloads Maven 3.10.0 |
| Python | 3.13.3 |
| uv | 0.9.24 |
| Node.js | 24.12.0 |
| npm | 11.6.2 |
| Docker | 29.1.4 |
| Docker Compose | v5.0.1 |
| Git | 2.49.0 |

Java 25 is a newer long-term release and Spring Initializr offers it. This machine has Java 21, which Spring Boot 4.1 supports, so the build is pinned to 21.

## Backend

| Piece | Version | Source |
|---|---|---|
| Spring Boot | 4.1.2 | Spring Initializr metadata, default stable boot version |
| springdoc-openapi | 3.2.0 | Initializr `springdoc-openapi` dependency for Boot 4.1 |
| Java language level | 21 | `java.version` in `backend/pom.xml` |
| Spotless | 3.10.4 | Maven Central latest release |
| google-java-format | 1.36.1 | Newest release Spotless 3.10.4 can run. 1.37.0 throws `InvocationTargetException` inside that Spotless release |
| JUnit 5 and Spring Boot Test | managed by Spring Boot 4.1.2 | starters `*-test` |

Spring Boot 4 names the servlet starter `spring-boot-starter-webmvc`.

## AI service

Resolved by `uv lock` on 2026-10-26. The lockfile is `ai-service/uv.lock`.

| Piece | Version |
|---|---|
| FastAPI | 0.162.0 |
| Pydantic | 2.14.1 |
| LangGraph | 1.2.17 |
| OpenAI Python SDK | 3.37.0 |
| Uvicorn | 0.54.0 |
| pytest | 9.1.1 |
| Ruff | 0.17.2 |
| mypy | 2.4.0 |

## Frontend

Taken from the current `create-vite` React TypeScript template (`create-vite` 9.3.1) plus the current test and format tools.

| Piece | Version |
|---|---|
| React | 19.3.1 |
| Vite | 8.3.5 |
| TypeScript | 6.0.3 |
| oxlint | 1.89.0 |
| Vitest | 5.0.5 |
| Testing Library React | 16.3.3 |
| jsdom | 29.1.1 |
| Prettier | 3.9.10 |

The Vite template lints with oxlint. Prettier formats. Vitest runs the tests.

## Database image for later phases

PostgreSQL 18 is the current stable major. Phase 10 will use the official image `postgres:18.6`. Phase 0 does not start a database.
