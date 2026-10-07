# be-interview-prep

Five Spring Boot features, each shipped as its own branch, pull request and merge.

| # | Question | PR link |
|---|---|---|
| 1 | Task Manager API | |
| 2 | URL Shortener | |
| 3 | Authentication & Roles | |
| 4 | Product Catalog | |
| 5 | Order Service | |

Video:

## Stack

Java 21, Spring Boot 3.5, Maven (wrapper included), PostgreSQL 17 in Docker, Liquibase XML migrations,
Spring Modulith for module boundaries, JUnit 5 with H2 for tests.

## Run the app

Requires Java 21 and Docker.

```bash
cp .env.example .env          # then set DB_USERNAME and DB_PASSWORD
docker compose up -d          # starts PostgreSQL on localhost:5432
./mvnw spring-boot:run        # applies Liquibase migrations, starts on :8080
```

Health check: `curl localhost:8080/actuator/health`.

## Run the tests

```bash
./mvnw verify
```

Tests run against an in-memory H2 database in PostgreSQL mode with the same Liquibase changelogs, so
no Docker is needed. `verify` also runs Spotless; fix formatting with `./mvnw spotless:apply`.

## Project layout

```
src/main/java/com/sreeyukthag/beinterviewprep/
  common/        shared API response, error handling, base entity (open module)
  <feature>/     one Spring Modulith module per question
src/main/resources/db/changelog/
  db.changelog-master.xml          includes each release in order
  releases/<version>/              one directory per release, changeset ids prefixed with the version
```

## Database versioning

Every schema change is a Liquibase XML changeset. Each feature ships as a minor release (`1.1.0` for
Q1 through `1.5.0` for Q5): the `pom.xml` version, the `releases/<version>/` directory and a closing
`tagDatabase` changeset all carry the same number, so a rollback can target any release tag.
Applied changesets are never edited; a fix is a new changeset.

## Error format

Every response, success or error, uses one JSON envelope:

```json
{ "success": false, "message": "Request validation failed", "errorCode": "VALIDATION_FAILED",
  "errors": [{ "field": "title", "message": "must not be blank" }], "timestamp": "..." }
```
