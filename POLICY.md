# Policy Statement: Prerequisite & Waitlist Assumptions

This document states the assumptions the implementation is built on, as required by
the assessment ("State the prerequisite and waitlist assumptions..."). The service
code and tests are expected to match this document exactly; if either needs to
change, update both together.

## Scope

A single `CourseOffering` has a fixed `capacity` and a fixed set of
`requiredPrerequisites` (course/credential codes, represented as opaque strings —
no prerequisite catalog or dependency graph is modeled). A `Learner` has a set of
`completedPrerequisites` that *can* change after registration — see "Eligibility
can change over time" below — but there is no modeled business rule that revokes
completed prerequisites; the mechanism exists because the store allows it, not
because the domain requires it.

## 1. Prerequisite / eligibility rule

> A learner is **eligible** for an offering if and only if
> `offering.requiredPrerequisites ⊆ learner.completedPrerequisites`
> (every required prerequisite has been completed). An offering with no
> required prerequisites is satisfied by every learner.

This is a static, single-offering check — no GPA, standing, term, or
capacity-unrelated business rule is modeled, per the assessment's scope
boundaries ("do not build a configurable policy engine").

Eligibility is checked at two points, not just one:

- **At enroll time** — a learner who is not eligible is never enrolled and
  never added to the waitlist (see Acceptance Criterion 4). They receive a
  distinct `INELIGIBLE` result, separate from a successful `WAITLISTED` result,
  so the caller can tell "you are in line" from "you don't qualify yet."
- **At promotion time** (when a seat frees up) — eligibility is re-checked
  against the current learner record rather than trusted from join time.

### Why re-check at promotion time?

A waitlist can sit for an arbitrary amount of time between when a learner joins
it and when a seat opens. Treating "eligible when they joined" as permanently
valid would let a learner's enroll-time eligibility outlive the state it was
based on. Re-checking at the moment of promotion is the conservative, correct
behavior for a gatekeeping rule like a prerequisite, and it is what makes the
skip-vs-remain decision below meaningful in the first place.

### Eligibility can change over time

A learner's record can be re-registered with a different `completedPrerequisites`
set after they have already joined a waitlist (e.g. a correction to their
transcript, or a prerequisite being revoked). The service does not special-case
this — it is a consequence of eligibility being re-checked against the current
record rather than cached at join time. This is also why the skip behavior
(section 3) is exercised in tests: a learner who was eligible when they joined
the waitlist can become ineligible by the time a seat opens, and must still be
skipped rather than block the queue.

## 2. Waitlist ordering

The waitlist is strict FIFO by join order. Join order is the order in which
`enroll()` calls were accepted onto the waitlist (first call in, first call
out), tracked via insertion-ordered storage — no separate timestamp field is
needed since the store itself preserves arrival order.

## 3. Skip vs. remain-waiting policy (the required decision)

> **Decision: an ineligible learner at the front of the waitlist is SKIPPED,
> not removed and not left blocking the queue.** The promotion scan continues
> to the next waitlisted learner in FIFO order and promotes the first one who
> *is* eligible. A skipped learner keeps their place on the waitlist and is
> reconsidered on every subsequent seat opening. They are removed from the
> waitlist only by an explicit drop request (not modeled as a separate
tier of this exercise beyond the waitlist's own drop behavior) — this
> process never removes them.

**Why skip, not block:** the alternative — leaving an ineligible learner at
the front of the queue until they become eligible — would stall seat
allocation for every learner behind them, indefinitely, on every future drop.
That defeats the purpose of a waitlist (getting *someone* eligible into the
seat promptly) and is unfair to everyone else in line. Skipping preserves
both throughput and FIFO fairness among eligible learners, while still
giving the skipped learner every future chance to be promoted once they
qualify — they are never penalized beyond being passed over for a seat they
are not (yet) eligible to take.

This policy is applied consistently: it is the same rule used whether one
seat opens or the promotion scan has to pass over several ineligible
learners in a row before finding an eligible one.

## 4. Enrollment & capacity invariants

- A learner is in at most one of {enrolled, waitlisted} for a given offering,
  never both, and never counted twice in either.
- `enrolled.size() <= capacity` always holds; a seat is never oversold.
- Joining the waitlist or enrolling is **idempotent per learner**: a repeated
  enroll request for a learner already enrolled or already waitlisted returns
  a distinct result (`ALREADY_ENROLLED` / `ALREADY_WAITLISTED`) and performs
  no mutation — it does not create a duplicate waitlist entry or move them in
  the queue.
- Dropping a learner who is not currently enrolled (never enrolled, already
  dropped, or a duplicate drop request) returns `NOT_ENROLLED` and performs no
  mutation — it never corrupts capacity or waitlist state, and never promotes
  as a side effect of a no-op drop.

## 5. Concurrency

All read-then-mutate sequences for a given offering (the enroll decision, and
the drop-then-promote sequence) are serialized by synchronizing on that
offering's instance. Two concurrent operations against the *same* offering
are therefore atomic with respect to each other; operations against
*different* offerings do not contend. This is sufficient for the in-memory,
single-process scope of this exercise and is what the focused concurrency
test (acceptance criterion 6) verifies: concurrent enroll requests against one
offering never push `enrolled.size()` past `capacity`.
