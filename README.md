# course-registration-waitlist

Focused AI-assisted assessment: enrollment, drop, and FIFO waitlist promotion
for one course offering. No starter project was supplied, so this is the
minimal-service fallback described in the assessment (Spring Boot + an
in-memory store, no database, no frontend).

See [POLICY.md](POLICY.md) for the prerequisite and waitlist assumptions,
[docs/DESIGN.md](docs/DESIGN.md) for the design specification,
[docs/AI_USAGE.md](docs/AI_USAGE.md) for the AI prompt/use record, and
[docs/LIMITATIONS.md](docs/LIMITATIONS.md) for known limitations and
follow-up risks.

## Prerequisites

Java 17+. No local Maven install is required — `./mvnw` (the Maven Wrapper)
downloads a pinned Maven version on first use.

## Build & test

```bash
./mvnw clean test
```

Runs the full suite: 7 `CourseOfferingServiceTest` cases (one per acceptance
criterion, including a focused concurrency test) plus the generated Spring
Boot context-load smoke test. Results land in `target/surefire-reports/`.

## Run

```bash
./mvnw spring-boot:run
```

Starts the service on `http://localhost:8080`. There is no supplied catalog
or learner-record system, so an offering and its learners must be registered
first via the setup endpoints below before `enroll`/`drop` do anything
useful.

## Manual walkthrough

The commands below were run against a live instance of this service to
verify the documented behavior end to end (not just asserted by unit tests).

```bash
# 1. Create an offering: 1 seat, requires MATH100
curl -s -X POST http://localhost:8080/offerings \
  -H 'Content-Type: application/json' \
  -d '{"id":"CS101","capacity":1,"requiredPrerequisites":["MATH100"]}'

# 2. Register learners
curl -s -X POST http://localhost:8080/learners \
  -H 'Content-Type: application/json' -d '{"id":"alice","completedPrerequisites":["MATH100"]}'
curl -s -X POST http://localhost:8080/learners \
  -H 'Content-Type: application/json' -d '{"id":"bob","completedPrerequisites":["MATH100"]}'
curl -s -X POST http://localhost:8080/learners \
  -H 'Content-Type: application/json' -d '{"id":"dave","completedPrerequisites":[]}'

# 3. alice enrolls -> seat available
curl -s -X POST http://localhost:8080/offerings/CS101/learners/alice/enroll
# {"outcome":"ENROLLED","offeringId":"CS101","learnerId":"alice"}

# 4. bob enrolls -> offering full, joins the waitlist
curl -s -X POST http://localhost:8080/offerings/CS101/learners/bob/enroll
# {"outcome":"WAITLISTED","offeringId":"CS101","learnerId":"bob"}

# 5. dave enrolls -> missing MATH100, ineligible (never enrolled or waitlisted)
curl -s -X POST http://localhost:8080/offerings/CS101/learners/dave/enroll
# {"outcome":"INELIGIBLE","offeringId":"CS101","learnerId":"dave"}

# 6. Check status
curl -s http://localhost:8080/offerings/CS101
# {"id":"CS101","capacity":1,"enrolledLearnerIds":["alice"],"waitlist":["bob"]}

# 7. alice drops -> bob (earliest eligible waitlisted learner) is promoted
curl -s -X POST http://localhost:8080/offerings/CS101/learners/alice/drop
# {"outcome":"DROPPED","offeringId":"CS101","learnerId":"alice","promotedLearnerId":"bob"}

curl -s http://localhost:8080/offerings/CS101
# {"id":"CS101","capacity":1,"enrolledLearnerIds":["bob"],"waitlist":[]}

# 8. Repeated/invalid drop of alice -> clear result, no state corruption
curl -s -X POST http://localhost:8080/offerings/CS101/learners/alice/drop
# {"outcome":"NOT_ENROLLED","offeringId":"CS101","learnerId":"alice","promotedLearnerId":null}
```

## API reference

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/offerings` | create an offering (setup only, not part of the acceptance criteria) |
| `POST` | `/learners` | register a learner and their completed prerequisites (setup only) |
| `GET` | `/offerings/{offeringId}` | current status: capacity, enrolled ids, waitlist in FIFO order |
| `POST` | `/offerings/{offeringId}/learners/{learnerId}/enroll` | the scoped enroll operation |
| `POST` | `/offerings/{offeringId}/learners/{learnerId}/drop` | the scoped drop/promote operation |

## Status

Complete for this exercise's scope: domain model, in-memory stores, the
enroll/drop/promotion logic, REST API, and all test cases are implemented
and passing (verified with `./mvnw clean test` and the manual walkthrough
above). See [docs/LIMITATIONS.md](docs/LIMITATIONS.md) for what's
deliberately left out.
