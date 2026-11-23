# AI Prompt & Use Record

> Required deliverable. Log entries as you go, not retroactively — record each
> prompt right after you use it, while the accept/edit/reject decision and
> verification step are still fresh. Do not include credentials, secrets, or
> unnecessary sensitive data in prompts recorded here.

Entry template:

```
## <short title>

**Context / file(s):** <what you were working on>
**Prompt:**
<the actual prompt text>

**Response (excerpt or summary):**
<what the AI produced, summarized if long>

**Decision:** accepted / edited / rejected — <why>
**Verification:** <how you independently checked this — test run, manual trace,
code review, etc. — and the result>
```

---

> **Note on how this log was produced:** every prompt below is this candidate's
> actual message to Claude (Claude Code) in chat, and Claude wrote the code
> directly into this repo — there was no separate "ask AI, then retype its
> answer" step. Verification steps were run and their raw output inspected
> before trusting the result (see the deadlock entry below for a concrete
> case this caught). That said, Claude running its own tests is not a
> substitute for the candidate's own read-through — the code and this log
> should still be independently reviewed before submission.

## 1. Project skeleton

**Context / file(s):** whole repo — initial scaffold.
**Prompt:**
"Set up the project skeleton and draft the policy statement"

**Response (excerpt or summary):**
Scaffolded a Spring Boot + Maven project (chosen over Python/plain-CLI after
being asked, to match the existing workspace's stack): domain model
(`CourseOffering`, `Learner`, outcome enums/result records), in-memory
`CourseOfferingStore`/`LearnerStore`, a `CourseOfferingService` interface with
the enroll/drop logic left as `TODO`s referencing `POLICY.md`, a REST
controller, and 7 empty (passing) test stubs — one per acceptance criterion.

**Decision:** accepted as-is. The stack choice itself wasn't left to the AI —
see the `AskUserQuestion` decision point for that.
**Verification:** ran `./mvnw compile` and `./mvnw test`; confirmed a clean
compile and all 8 stub tests (7 service stubs + the generated Spring context
smoke test) passing.

## 2. Policy statement

**Context / file(s):** `POLICY.md`.
**Prompt:** (same message as above)

**Response (excerpt or summary):** drafted the prerequisite rule (subset-of
completed-prerequisites), the FIFO waitlist ordering rule, and — the decision
the assessment explicitly calls for — that an ineligible learner reaching the
front of the waitlist is **skipped**, not removed and not left blocking the
queue, with the promotion scan continuing to the next eligible candidate.

**Decision:** accepted the skip-vs-block reasoning (blocking would stall
every learner behind an ineligible one, indefinitely, on every future drop —
skip preserves both throughput and FIFO fairness). Caught and fixed one gap
myself on a later pass: the doc referenced a section ("Eligibility can change
over time") that was never actually written — added it before relying on the
policy to write the skip-promotion test, since that test depends on a
learner's eligibility being able to change after they join the waitlist.
**Verification:** re-read the full document end to end for internal
consistency (every cross-reference resolves) before building the
implementation on top of it.

## 3. enroll()/drop() implementation

**Context / file(s):**
`src/main/java/com/assessment/coursewaitlist/service/InMemoryCourseOfferingService.java`
**Prompt:**
"Yes, implement enroll/drop logic and log AI use"

**Response (excerpt or summary):** implemented `enroll()` as a
synchronized-on-the-offering check sequence (already-enrolled →
already-waitlisted → ineligible → seat-available-enroll → else-waitlist) and
`drop()` as remove-then-promote, where promotion walks the waitlist in FIFO
(insertion) order, skips any candidate whose current `Learner` record is no
longer eligible, and promotes the first eligible one found.

**Decision:** accepted the control flow as specified by `POLICY.md` with no
changes — it's a direct translation of the five-step list already in the
`TODO` comments. One thing checked explicitly during review: `drop()`'s
promotion loop iterates `offering.getWaitlist()` (a live, unmodifiable *view*
backed by the same `LinkedHashSet` it mutates on a match) and returns
immediately on the one mutation — confirmed this does not throw
`ConcurrentModificationException`, since the iterator is never advanced again
after the mutating call.
**Verification:** compiled (`./mvnw compile`), then ran the full test suite —
see entry 4 for how that first run actually went.

## 4. Test bodies — including a deadlock the first run caught

**Context / file(s):**
`src/test/java/com/assessment/coursewaitlist/service/CourseOfferingServiceTest.java`
**Prompt:** (same message as entry 3)

**Response (excerpt or summary):** filled in all 7 stubs, including a focused
concurrency test (`concurrentEnrollRequestsNeverExceedCapacity`) using a
16-thread fixed pool, 50 learners, and a two-`CountDownLatch` rendezvous
barrier (`ready`/`start`) meant to make all 50 enroll calls hit the
synchronized block at roughly the same instant.

**Decision:** initially accepted without reading it closely enough — this is
the one worth being candid about. Running `./mvnw test` the first time hung
indefinitely instead of finishing in the expected ~1 second. Rather than
assume it was "just slow" and wait it out, I checked the actual process
state with `ps` (confirmed the JVM had barely accrued CPU time over minutes
of wall-clock time — not compute-bound) and then `jstack` on the running
surefire JVM, which showed the main thread parked on `ready.await()`.

The bug: with a 50-task workload but only a 16-thread pool, all 16 running
threads reach `start.await()` and block — they can never return to the pool
to pick up the remaining 34 tasks, so `ready` (which needs 50 countdowns) can
never reach zero. Classic insufficient-pool-size deadlock in the test itself,
not in the service code under test.

**Fix:** sized the executor to `learnerCount` (so every task can reach the
rendezvous barrier concurrently) instead of a fixed 16, with a comment
explaining why the pool size is load-bearing.
**Verification:** killed the hung JVM (`kill -9`), reran `./mvnw test`
in the foreground — exit code 0, `CourseOfferingServiceTest`: 7 run / 0
failures / 0 errors in 0.102s, `CourseWaitlistApplicationTests`: 1/1 in
2.864s. Confirmed the surefire report timestamps were from the fresh run
(not stale files left over from the earlier skeleton-only pass).

## 5. Final review pass — dead code and an unprotected read path

**Context / file(s):**
`store/CourseOfferingStore.java`, `api/CourseOfferingController.java`,
`docs/DESIGN.md`.
**Prompt:**
"Review the whole repo once more before I submit it." Followed mid-review by
"There should not be any dead code, unwanted implementations. should be
exactly as per the requirements."

**Response (excerpt or summary):** re-read every source file end to end
(not just the ones touched most recently) against the assessment's actual
requirements list, rather than re-skimming my own earlier summary of them.
Two findings:

1. `CourseOfferingStore.exists(String)` was never called anywhere
   (`grep -rn "\.exists(" src/` — zero hits) — dead code left over from
   initial scaffolding. Removed.
2. `GET /offerings/{offeringId}` built its response by calling
   `List.copyOf()` over `getEnrolledLearnerIds()`/`getWaitlist()` — both
   live, unmodifiable *views* over the same `LinkedHashSet` fields that
   `enroll()`/`drop()` mutate under `synchronized(offering)`. The GET path
   itself took no lock. A concurrent GET during a mutation can throw
   `ConcurrentModificationException` or return a torn read — a real bug,
   not a style nit, and one the existing concurrency test couldn't have
   caught since it only exercises concurrent `enroll()`, never a concurrent
   read.

**Decision:** both accepted. Removed the dead method outright rather than
keeping it "in case it's needed" — nothing in the acceptance criteria or
API calls for an existence check as its own operation. Fixed the race by
having the controller synchronize on the same offering instance the service
already locks for mutations, matching the pattern used everywhere else in
this codebase rather than inventing a new one (e.g. a repository-style
snapshot method).
**Verification:** `grep` confirmed `exists()` had no callers before removal.
`./mvnw clean test` after both changes: exit 0, same 7/1 test counts as
before — expected, since neither fix changes externally observable
single-threaded behavior, only removes an unused method and closes a race
that the existing tests weren't structured to exercise. Updated
`docs/DESIGN.md`'s concurrency section to describe the now-synchronized
read path so the design doc doesn't go stale relative to the code.
