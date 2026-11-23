package com.assessment.coursewaitlist.service;

import com.assessment.coursewaitlist.domain.CourseOffering;
import com.assessment.coursewaitlist.domain.DropOutcome;
import com.assessment.coursewaitlist.domain.DropResult;
import com.assessment.coursewaitlist.domain.EnrollmentOutcome;
import com.assessment.coursewaitlist.domain.EnrollmentResult;
import com.assessment.coursewaitlist.domain.Learner;
import com.assessment.coursewaitlist.store.CourseOfferingStore;
import com.assessment.coursewaitlist.store.LearnerStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Scenarios required by the assessment's acceptance criteria (see ../../../../../../../POLICY.md
 * and the Acceptance Criteria / "Tests cover" bullet in the assessment PDF).
 */
class CourseOfferingServiceTest {

    private CourseOfferingStore offeringStore;
    private LearnerStore learnerStore;
    private CourseOfferingService service;

    @BeforeEach
    void setUp() {
        offeringStore = new CourseOfferingStore();
        learnerStore = new LearnerStore();
        service = new InMemoryCourseOfferingService(offeringStore, learnerStore);
    }

    @Test
    void enrollSucceedsWhenCapacityRemainsAndPrerequisitesAreSatisfied() {
        service.createOffering("CS101", 2, Set.of("MATH100"));
        learnerStore.save(new Learner("alice", Set.of("MATH100")));

        EnrollmentResult result = service.enroll("CS101", "alice");

        assertThat(result.outcome()).isEqualTo(EnrollmentOutcome.ENROLLED);
        assertThat(service.getOffering("CS101").getEnrolledLearnerIds()).containsExactly("alice");
        assertThat(service.getOffering("CS101").getWaitlist()).isEmpty();
    }

    @Test
    void enrollJoinsWaitlistOnceWhenOfferingIsFull() {
        service.createOffering("CS101", 1, Set.of());
        learnerStore.save(new Learner("alice", Set.of()));
        learnerStore.save(new Learner("bob", Set.of()));

        service.enroll("CS101", "alice");
        EnrollmentResult result = service.enroll("CS101", "bob");

        assertThat(result.outcome()).isEqualTo(EnrollmentOutcome.WAITLISTED);
        assertThat(service.getOffering("CS101").getWaitlist()).containsExactly("bob");
        assertThat(service.getOffering("CS101").getEnrolledLearnerIds()).containsExactly("alice");
    }

    @Test
    void duplicateEnrollRequestDoesNotCreateADuplicateRecord() {
        service.createOffering("CS101", 1, Set.of());
        learnerStore.save(new Learner("alice", Set.of()));
        learnerStore.save(new Learner("bob", Set.of()));

        service.enroll("CS101", "alice");
        EnrollmentResult duplicateEnrolled = service.enroll("CS101", "alice");

        assertThat(duplicateEnrolled.outcome()).isEqualTo(EnrollmentOutcome.ALREADY_ENROLLED);
        assertThat(service.getOffering("CS101").getEnrolledLearnerIds()).containsExactly("alice");

        service.enroll("CS101", "bob");
        EnrollmentResult duplicateWaitlisted = service.enroll("CS101", "bob");

        assertThat(duplicateWaitlisted.outcome()).isEqualTo(EnrollmentOutcome.ALREADY_WAITLISTED);
        assertThat(service.getOffering("CS101").getWaitlist()).containsExactly("bob");
    }

    @Test
    void dropPromotesTheEarliestEligibleWaitlistedLearner() {
        service.createOffering("CS101", 1, Set.of());
        learnerStore.save(new Learner("alice", Set.of()));
        learnerStore.save(new Learner("bob", Set.of()));
        learnerStore.save(new Learner("carol", Set.of()));

        service.enroll("CS101", "alice");
        service.enroll("CS101", "bob");
        service.enroll("CS101", "carol");

        DropResult result = service.drop("CS101", "alice");

        assertThat(result.outcome()).isEqualTo(DropOutcome.DROPPED);
        assertThat(result.promotedLearnerId()).isEqualTo("bob");
        assertThat(service.getOffering("CS101").getEnrolledLearnerIds()).containsExactly("bob");
        assertThat(service.getOffering("CS101").getWaitlist()).containsExactly("carol");
    }

    @Test
    void ineligibleLearnerIsNeitherEnrolledNorPromoted() {
        service.createOffering("CS101", 1, Set.of("MATH100"));
        learnerStore.save(new Learner("alice", Set.of("MATH100")));
        learnerStore.save(new Learner("bob", Set.of("MATH100")));
        learnerStore.save(new Learner("carol", Set.of("MATH100")));

        // direct ineligible enroll attempt: never enrolled or waitlisted
        learnerStore.save(new Learner("dave", Set.of()));
        EnrollmentResult daveResult = service.enroll("CS101", "dave");
        assertThat(daveResult.outcome()).isEqualTo(EnrollmentOutcome.INELIGIBLE);
        assertThat(service.getOffering("CS101").getEnrolledLearnerIds()).doesNotContain("dave");
        assertThat(service.getOffering("CS101").getWaitlist()).doesNotContain("dave");

        // bob and carol join the waitlist while eligible
        service.enroll("CS101", "alice"); // takes the one seat
        service.enroll("CS101", "bob");
        service.enroll("CS101", "carol");

        // bob's prerequisite record is later revoked while still waitlisted
        learnerStore.save(new Learner("bob", Set.of()));

        DropResult result = service.drop("CS101", "alice");

        // bob is skipped (per POLICY.md section 3), not enrolled and not removed from the waitlist
        assertThat(result.promotedLearnerId()).isEqualTo("carol");
        CourseOffering offering = service.getOffering("CS101");
        assertThat(offering.getEnrolledLearnerIds()).containsExactly("carol");
        assertThat(offering.getWaitlist()).containsExactly("bob");
    }

    @Test
    void invalidOrRepeatedDropReturnsAClearResultWithoutCorruptingState() {
        service.createOffering("CS101", 1, Set.of());
        learnerStore.save(new Learner("alice", Set.of()));

        DropResult neverEnrolled = service.drop("CS101", "alice");
        assertThat(neverEnrolled.outcome()).isEqualTo(DropOutcome.NOT_ENROLLED);
        assertThat(neverEnrolled.promotedLearnerId()).isNull();

        service.enroll("CS101", "alice");
        DropResult firstDrop = service.drop("CS101", "alice");
        assertThat(firstDrop.outcome()).isEqualTo(DropOutcome.DROPPED);

        DropResult repeatedDrop = service.drop("CS101", "alice");
        assertThat(repeatedDrop.outcome()).isEqualTo(DropOutcome.NOT_ENROLLED);
        assertThat(repeatedDrop.promotedLearnerId()).isNull();

        assertThat(service.getOffering("CS101").getEnrolledLearnerIds()).isEmpty();
        assertThat(service.getOffering("CS101").getWaitlist()).isEmpty();
    }

    @Test
    void concurrentEnrollRequestsNeverExceedCapacity() throws Exception {
        int capacity = 5;
        int learnerCount = 50;
        service.createOffering("CS101", capacity, Set.of());

        List<String> learnerIds = new ArrayList<>();
        for (int i = 0; i < learnerCount; i++) {
            String id = "learner-" + i;
            learnerIds.add(id);
            learnerStore.save(new Learner(id, Set.of()));
        }

        // Pool size must be >= learnerCount: every task parks on `start` after counting
        // down `ready`, so a smaller pool would leave `ready` forever short of zero
        // (threads queued behind the running ones can never reach their countDown call).
        ExecutorService executor = Executors.newFixedThreadPool(learnerCount);
        CountDownLatch ready = new CountDownLatch(learnerCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<EnrollmentResult>> futures = new ArrayList<>();

        for (String id : learnerIds) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                return service.enroll("CS101", id);
            }));
        }

        ready.await();
        start.countDown();
        for (Future<EnrollmentResult> future : futures) {
            future.get(5, TimeUnit.SECONDS);
        }
        executor.shutdown();

        CourseOffering offering = service.getOffering("CS101");
        assertThat(offering.getEnrolledLearnerIds()).hasSize(capacity);
        assertThat(offering.getWaitlist()).hasSize(learnerCount - capacity);
        assertThat(offering.getEnrolledLearnerIds()).doesNotContainAnyElementsOf(offering.getWaitlist());
    }
}
