# Release Manager

We run many microservices on many environments, and each service ships on its own schedule. A
deployment watcher tells this service every time something is deployed: the service name and its
version. The release manager turns that stream into one number per environment, the
**SystemVersion**. It goes up every time the set of deployed versions changes, and for any past
value you can ask "what exactly was running back then?"

Kotlin, Spring Boot 4 (Web MVC, `JdbcClient`, Flyway, Bean Validation), PostgreSQL 16.

## Running

You need Docker. Everything else is handled by Gradle.

```bash
./gradlew bootRun     # starts PostgreSQL from compose.yaml, then the app on port 8080
./gradlew test        # unit tests, plus integration tests on a PostgreSQL Testcontainer

./gradlew bootJar && docker compose --profile full up --build   # app and database both in Docker
docker compose --profile full down -v                           # stop and drop the database volume
```

- Swagger UI: <http://localhost:8080/swagger-ui.html>, OpenAPI JSON at `/v3/api-docs`
- Health: <http://localhost:8080/actuator/health>

`bootRun` relies on Spring Boot's Docker Compose support: it brings up the `postgres` service and
points the datasource at it. The `app` service is behind the `full` profile, so `bootRun` never
starts it. To run against a database you already have, set `DATABASE_URL`, `DATABASE_USERNAME`
and `DATABASE_PASSWORD`.

The build targets Java 21. The code has nothing version-specific in it, so the toolchain line in
`build.gradle.kts` can be raised if you prefer a newer JDK.

### The scenario from the assignment

```bash
curl -s -X POST localhost:8080/deploy -H 'Content-Type: application/json' -d '{"name":"Service A","version":1}'
# 1
curl -s -X POST localhost:8080/deploy -H 'Content-Type: application/json' -d '{"name":"Service B","version":1}'
# 2
curl -s -X POST localhost:8080/deploy -H 'Content-Type: application/json' -d '{"name":"Service A","version":2}'
# 3
curl -s -X POST localhost:8080/deploy -H 'Content-Type: application/json' -d '{"name":"Service B","version":1}'
# 3      <- nothing changed, nothing written

curl -s 'localhost:8080/services?systemVersion=2'
# [{"name":"Service A","version":1},{"name":"Service B","version":1}]
curl -s 'localhost:8080/services?systemVersion=3'
# [{"name":"Service A","version":2},{"name":"Service B","version":1}]
```

### With an explicit environment

```bash
curl -s -X POST localhost:8080/deploy -H 'Content-Type: application/json' \
     -d '{"name":"Service A","version":7,"environment":"prod"}'
# 1      <- prod counts on its own
curl -s 'localhost:8080/services?systemVersion=1&environment=prod'
# [{"name":"Service A","version":7}]
```

### Errors

Errors come back as RFC 9457 problem details. Bad input, or a missing or non-numeric
`systemVersion`, is a `400`. A SystemVersion that does not exist in that environment is a `404`,
and so is an environment that has never seen a deploy. Every error has a stable `type` URI that a
client can switch on. Validation errors also carry an `invalidFields` map, so nobody has to parse
the `detail` text.

```json
{"type":"/errors/invalid_request","status":400,"title":"Bad Request",
 "detail":"name: must not be blank; version: must be greater than or equal to 1",
 "invalidFields":{"name":"must not be blank","version":"must be greater than or equal to 1"},
 "instance":"/deploy"}
{"type":"/errors/unknown_system_version","status":404,"title":"Not Found",
 "detail":"System version 9 does not exist in environment 'default'",
 "environment":"default","systemVersion":9,"instance":"/services"}
```

## Project structure

```
api/          controller, request/response DTOs, ProblemDetail exception handler
service/      ReleaseManagerService: the two use cases and their transaction boundaries
domain/       Environment, SystemVersion, DeployedService, Deployment (plain Kotlin, no framework)
repository/   ReleaseRepository interface and its JdbcClient implementation
resources/db/migration/V1__init.sql
```

Tests mirror the same packages. `ReleaseManagerServiceTest` is a unit test with a mocked
repository. `ReleaseManagerAcceptanceTest` replays the assignment scenario through MockMvc against a
real database. `ConcurrentDeployTest` fires 50 threads at the real database and checks the locking.

## Design

### Assumptions and resolved ambiguities

The assignment leaves a few things open. Here is how I read them.

- **Field names.** The prose says `serviceName` and `serviceVersionNumber`, the example says `name`
  and `version`. I followed the example. The response is the bare SystemVersion integer, also as in
  the example.
- **Environments.** The text describes several environments but the endpoints carry no environment.
  I added an optional `environment` field to both endpoints. When it is missing, the value is
  `default`, so the example works unchanged. Each environment has its own SystemVersion sequence,
  starting at 1. Environments are created on first deploy; there is no registration step.
- **What counts as a change.** A version that differs from the one currently known for that service
  in that environment. A brand new service is a change. A rollback to a lower version is a change
  too: the release manager records what is deployed, it does not judge it.
- **Unchanged deploys are idempotent.** The current SystemVersion is returned and nothing is written.
- **SystemVersion is gapless** and starts at 1 in every environment.
- **Names.** Service names are case-sensitive and trimmed. Environment names are case-sensitive,
  trimmed, at most 100 characters, and match `[A-Za-z0-9._-]+`.
- **Removing a service is out of scope.** The API has no way to say it. The data model would take a
  tombstone row later without changing shape.
- **Reports are applied in the order they arrive.** `deployed_at` is when the report was recorded,
  not when the deploy happened. Over a message bus this leaves two gaps that the current payload
  cannot close. Deploys A1, A2, A3 delivered as A1, A3, A2 end up as three versions with A2 last. A
  redelivery of A2 after A3 looks exactly like a rollback and creates a version. Fixing either needs
  information from the watcher: a `deployedAt` set at the source, or an `eventId` for deduplication
  plus a per-service `deploymentSequence` for ordering. The sequence is the sturdier option, no
  clock skew and no ties. With it, a sequence not greater than the last one recorded is logged as
  stale and never touches the ledger. I did not implement this because the consumer should not
  guess values the producer did not send.
- **`GET /services` requires `systemVersion`.** Leaving it out is a `400`, not "give me the current
  one".

### How it works

Two tables. `deployment` is an append-only ledger: one row per `(environment, system_version)`,
holding the single deployment that caused that version. Rows are never updated or deleted.
`release_state` has one row per environment with the current SystemVersion.

### Decisions

**A ledger, not a copy of all services per version.** Copying the full list on every change makes
writes grow with the number of services and the table grow with versions times services. The ledger
writes one row per change and still answers any historical question with one indexed query.

**A row lock per environment.** Two deploys arriving together must not both become version 8. The
`FOR UPDATE` on the environment's counter row makes the second one wait, and when it resumes it
re-reads the committed counter, so it decides based on what the first one did. The lock lives
exactly as long as the transaction and is released on commit, rollback or crash. Other options I
considered:

- Optimistic locking works but turns a burst of deploys into a retry storm.
- JVM locks (`synchronized`, `Mutex`) do not reach across replicas.
- A database sequence is not transactional, leaves gaps, and cannot express "same version, same
  number".
- ...

Locks are per environment, so `staging` never waits for `prod`.

**Why `release_state` exists at all.** You cannot `FOR UPDATE` a `MAX()` and you cannot lock a row
that does not exist yet. The counter table is the thing to hold while you add to the ledger.
Because environments appear on first use, the row is not seeded by the migration; the
`ON CONFLICT DO NOTHING` insert creates it on demand. Two concurrent first deploys serialize on the
unique index, so the second one sees the committed row when it reaches `FOR UPDATE`. Bootstrap and
deploy are one transaction, so a failed deploy leaves no orphan counter row.


**`200` with a bare integer.** The example shows `Response: 1`. A deploy report is idempotent, it may
or may not create a version, and the response looks the same either way, so `201` would be
misleading.

**`404`, not an empty list, for an unknown version.** An empty list would mean "nothing was
deployed", which is a different statement from "this version never existed".

**Environment as an optional field.** One instance per environment would stop a single watcher from
reporting for all of them. Deriving the environment from credentials is the right long-term shape
but needs authentication, which is out of scope. An optional field keeps the example valid and is
easy to replace later.

### What Can be done next

- A RabbitMQ consumer that calls the same `ReleaseManagerService`, so HTTP becomes one of two
  inbound adapters.
- A tombstone ledger row for service removal (service decommission).
- An inbound-event audit log keyed by `eventId`, recording the outcome of each event: applied,
  no change, stale, duplicate.
- ...
