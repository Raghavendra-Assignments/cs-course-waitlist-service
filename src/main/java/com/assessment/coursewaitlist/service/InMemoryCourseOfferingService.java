package com.assessment.coursewaitlist.service;

import com.assessment.coursewaitlist.domain.CourseOffering;
import com.assessment.coursewaitlist.domain.DropOutcome;
import com.assessment.coursewaitlist.domain.DropResult;
import com.assessment.coursewaitlist.domain.EnrollmentOutcome;
import com.assessment.coursewaitlist.domain.EnrollmentResult;
import com.assessment.coursewaitlist.domain.Learner;
import com.assessment.coursewaitlist.store.CourseOfferingStore;
import com.assessment.coursewaitlist.store.LearnerStore;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class InMemoryCourseOfferingService implements CourseOfferingService {

    private final CourseOfferingStore offeringStore;
    private final LearnerStore learnerStore;

    public InMemoryCourseOfferingService(CourseOfferingStore offeringStore, LearnerStore learnerStore) {
        this.offeringStore = offeringStore;
        this.learnerStore = learnerStore;
    }

    @Override
    public void createOffering(String offeringId, int capacity, Set<String> requiredPrerequisites) {
        offeringStore.save(new CourseOffering(offeringId, capacity, requiredPrerequisites));
    }

    @Override
    public EnrollmentResult enroll(String offeringId, String learnerId) {
        CourseOffering offering = offeringStore.getOrThrow(offeringId);
        Learner learner = learnerStore.getOrThrow(learnerId);

        synchronized (offering) {
            if (offering.isEnrolled(learnerId)) {
                return new EnrollmentResult(EnrollmentOutcome.ALREADY_ENROLLED, offeringId, learnerId);
            }
            if (offering.isWaitlisted(learnerId)) {
                return new EnrollmentResult(EnrollmentOutcome.ALREADY_WAITLISTED, offeringId, learnerId);
            }
            if (!isEligible(learner, offering.getRequiredPrerequisites())) {
                return new EnrollmentResult(EnrollmentOutcome.INELIGIBLE, offeringId, learnerId);
            }
            if (offering.hasSeatAvailable()) {
                offering.addEnrolled(learnerId);
                return new EnrollmentResult(EnrollmentOutcome.ENROLLED, offeringId, learnerId);
            }
            offering.addToWaitlist(learnerId);
            return new EnrollmentResult(EnrollmentOutcome.WAITLISTED, offeringId, learnerId);
        }
    }

    @Override
    public DropResult drop(String offeringId, String learnerId) {
        CourseOffering offering = offeringStore.getOrThrow(offeringId);

        synchronized (offering) {
            if (!offering.isEnrolled(learnerId)) {
                return new DropResult(DropOutcome.NOT_ENROLLED, offeringId, learnerId, null);
            }
            offering.removeEnrolled(learnerId);

            String promotedLearnerId = promoteEarliestEligibleWaitlisted(offering);
            return new DropResult(DropOutcome.DROPPED, offeringId, learnerId, promotedLearnerId);
        }
    }

    @Override
    public CourseOffering getOffering(String offeringId) {
        return offeringStore.getOrThrow(offeringId);
    }

    private boolean isEligible(Learner learner, Set<String> requiredPrerequisites) {
        return learner.getCompletedPrerequisites().containsAll(requiredPrerequisites);
    }

    /**
     * Scans the waitlist in FIFO order and promotes the first eligible candidate.
     * An ineligible candidate is skipped (left on the waitlist, not removed) per
     * POLICY.md section 3 - the scan does not stop or remove them, it just moves on.
     * Returns null if no waitlisted learner is currently eligible.
     */
    private String promoteEarliestEligibleWaitlisted(CourseOffering offering) {
        for (String candidateId : offering.getWaitlist()) {
            Learner candidate = learnerStore.getOrThrow(candidateId);
            if (isEligible(candidate, offering.getRequiredPrerequisites())) {
                offering.removeFromWaitlist(candidateId);
                offering.addEnrolled(candidateId);
                return candidateId;
            }
        }
        return null;
    }
}
