# Limitations & Follow-up Risks

Concise, scoped to what this exercise's implementation actually revealed —
not a wishlist of unbuilt features already covered by the assessment's scope
boundaries (no frontend, no catalog, no auth, etc. are intentional, not
"limitations").

1. **Re-creating an offering silently discards its live state.**
   `createOffering()` calls `CourseOfferingStore.save()`, which is a plain
   `Map.put()` — calling it again with an id that already exists replaces
   the `CourseOffering` object wholesale, dropping any current
   enrollments/waitlist with no warning. Fine for this exercise's
   test/setup-only use of that endpoint; a real admin API would need to
   reject the duplicate id or require an explicit, intentional
   reset/merge operation.

2. **An already-enrolled learner's eligibility is never re-checked after
   enrollment.** Eligibility is checked at enroll time and, separately, at
   promotion time for waitlisted learners (see POLICY.md section 1) — but
   once someone is enrolled, nothing revisits that decision even if their
   prerequisite record is later revoked. This is a deliberate scope
   boundary (retroactively dis-enrolling someone is a different, much
   larger feature), but it means "enrolled" is not a continuously-verified
   guarantee, only a point-in-time one.

3. **No input validation on the setup endpoints.** `POST /offerings`
   accepts any `capacity` value, including zero or negative — which
   doesn't corrupt state (every enroll attempt simply waitlists, since
   `hasSeatAvailable()` is false), but it's accepted silently rather than
   rejected, which would likely surprise a real caller.

4. **No persistence.** All state lives in two in-memory maps for the
   process lifetime; a restart loses every enrollment and waitlist. This
   matches the assessment's "in-memory store is sufficient" fallback, but
   it's the first thing that would need to change for any real deployment.

5. **Concurrency safety is single-process only.** `enroll()`/`drop()`
   synchronize on the in-memory `CourseOffering` instance, which only
   serializes calls within one JVM. Running more than one instance of this
   service against the same offering (e.g. behind a load balancer) would
   reintroduce the exact race conditions the concurrency test guards
   against — a real multi-instance deployment would need a database
   transaction or a distributed lock instead.
