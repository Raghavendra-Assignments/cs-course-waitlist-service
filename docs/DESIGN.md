# Design Specification

Scoped to the one feature slice this exercise asks for: enrollment, drop, and
FIFO waitlist promotion for a single course offering. No starter project was
supplied, so this is the minimal-service fallback (Spring Boot + an in-memory
store) described in the assessment — not a general registration platform.

## Service boundary

One service, `CourseOfferingService`, owns all state transitions for
enrollment and waitlist membership. It is the only thing allowed to mutate a
`CourseOffering`; callers (the REST layer, tests) only ever read offering
state back or ask the service to change it.

Two supporting collaborators, both in-memory and unscoped by any notion of
term or catalog:

- `CourseOfferingStore` — one `CourseOffering` per offering id.
- `LearnerStore` — one `Learner` (id + completed prerequisites) per learner id.

There is deliberately no "registration system," "catalog service," or
"learner profile service" — those are out of scope per the assessment's
boundaries. The two stores exist only so `enroll`/`drop` have something to
look up; populating them is a setup concern, not part of the scoped feature,
which is why those operations are plain `save()` calls with no validation,
versioning, or history.

## Data / API design

**Domain model** (`domain/`):

- `Learner(id, completedPrerequisites: Set<String>)` — immutable once
  constructed; "changing" a learner's prerequisites means re-saving a new
  `Learner` under the same id (see POLICY.md, "Eligibility can change over
  time").
- `CourseOffering(id, capacity, requiredPrerequisites: Set<String>)` plus two
  internal collections: `enrolledLearnerIds` and `waitlist`, both
  `LinkedHashSet<String>`. The waitlist's iteration order *is* FIFO order —
  there is no separate timestamp or sequence field, because insertion order
  already gives FIFO semantics for free from the collection itself.
- `EnrollmentResult(outcome, offeringId, learnerId)` and
  `DropResult(outcome, offeringId, learnerId, promotedLearnerId)` — the
  service's return values double as the REST response bodies. `outcome` is
  one of `EnrollmentOutcome` (`ENROLLED`, `WAITLISTED`, `ALREADY_ENROLLED`,
  `ALREADY_WAITLISTED`, `INELIGIBLE`) or `DropOutcome` (`DROPPED`,
  `NOT_ENROLLED`) — distinct enum values rather than booleans or thrown
  exceptions, because the caller needs to distinguish e.g. "waitlisted" from
  "already waitlisted" from "ineligible," and none of those are error
  conditions worth an exception.

**REST API** (`CourseOfferingController`):

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/offerings` | create an offering (setup, not part of the acceptance criteria) |
| `POST` | `/learners` | register a learner and their completed prerequisites (setup) |
| `GET` | `/offerings/{offeringId}` | current status: capacity, enrolled ids, waitlist (in FIFO order) |
| `POST` | `/offerings/{offeringId}/learners/{learnerId}/enroll` | the scoped enroll operation |
| `POST` | `/offerings/{offeringId}/learners/{learnerId}/drop` | the scoped drop/promote operation |

The first two exist only because this exercise has no supplied catalog or
learner-record system to seed data from; they are intentionally the
thinnest possible stand-in (no auth, no validation beyond what the service
layer already does), not a real admin API. `OfferingNotFoundException` /
`LearnerNotFoundException` map to `404` via `ApiExceptionHandler`; every
other outcome is a normal `200` with a distinguishing `outcome` field, since
"ineligible" or "already enrolled" are expected results, not errors.

## Enrollment & capacity invariants

See [../POLICY.md](../POLICY.md) section 4 for the invariants themselves
(never enrolled-and-waitlisted at once, capacity never oversold, enroll/drop
are idempotent per learner). The implementation enforces them structurally
rather than with runtime assertions: `enroll()` checks
`isEnrolled`/`isWaitlisted` before ever adding to either collection, and
`hasSeatAvailable()` is the single gate between "enroll" and "waitlist" — so
by construction there is no code path that adds a learner to both, or past
capacity.

## Waitlist promotion rule

See [../POLICY.md](../POLICY.md) sections 1–3 for the eligibility rule and
the skip-vs-block decision. Implementation-wise, promotion
(`promoteEarliestEligibleWaitlisted`) is a single linear scan of the
waitlist in iteration (FIFO) order, re-checking eligibility against the
*current* `Learner` record for each candidate, stopping at the first
eligible one. Candidates it passes over are left untouched — the method
either mutates exactly once (promote) or not at all (nobody eligible).

This is O(waitlist length) per drop, which is the right tradeoff at this
scope: a single scan is simpler and more obviously correct than maintaining
a secondary eligible-only index, and the exercise's bound ("one offering")
means the waitlist is never large enough for that to matter.

## Concurrency / persistence approach

See [../POLICY.md](../POLICY.md) section 5. Concretely: `enroll()` and
`drop()` each synchronize on the specific `CourseOffering` instance for their
entire read-then-mutate sequence, so two concurrent calls against the same
offering serialize, while calls against different offerings don't contend
with each other at all (no single global lock). The stores themselves use
`ConcurrentHashMap` so lookups by id are safe without needing the offering
lock.

The `GET /offerings/{offeringId}` read path takes the same per-offering lock
before building its response (`CourseOfferingController.getOffering`). This
isn't optional: `getEnrolledLearnerIds()`/`getWaitlist()` return a live,
unmodifiable *view* over the same `LinkedHashSet` that `enroll()`/`drop()`
mutate, not a snapshot — reading that view concurrently with a mutation
(via `List.copyOf()` in `OfferingStatusResponse.from`) can throw
`ConcurrentModificationException`. This was found during review, not design
— see AI_USAGE.md.

There is no persistence layer by design — everything lives in two in-memory
maps for the lifetime of the process, matching the assessment's "in-memory
store is sufficient" fallback. A real registration system would need a
transactional data store so promotion survives a crash between "seat freed"
and "learner notified," but that's explicitly out of scope here.

## Key assumptions / tradeoffs

- **No prerequisite catalog or dependency graph.** Prerequisites are opaque
  string codes; "satisfied" is a plain subset check (POLICY.md section 1).
  A real system would need to model prerequisite *courses* with their own
  states, which is exactly the kind of scope the assessment says not to
  build.
- **Learner records are mutable via overwrite, not a dedicated update API.**
  `LearnerStore.save()` doubles as both "register" and "update" (last write
  wins). This was a deliberate minimal choice to make the promotion-time
  re-check (section 1 of POLICY.md) actually testable without inventing a
  learner-management API that isn't part of this feature.
- **Single-process, in-memory, no auth.** Acceptable for a bounded take-home
  exercise; would not be acceptable for a real waitlist service, where
  durability and authorization are core requirements, not afterthoughts.
- **No notification step.** A promoted learner is just enrolled; whether
  they are *told* they got a seat is a separate concern (email/notification
  service) that the assessment's scope boundaries exclude ("do not add...
  identity integrations" and similar).
- **REST setup endpoints are not a real admin API.** `POST /offerings` and
  `POST /learners` have no input validation, auth, or idempotency guarantees
  beyond what `Map.put` gives you for free. They exist solely so the scoped
  feature can be exercised over HTTP; building them out further would be
  scope creep the assessment explicitly warns against.
