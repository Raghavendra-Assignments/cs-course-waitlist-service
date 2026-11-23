package com.assessment.coursewaitlist.service;

import com.assessment.coursewaitlist.domain.CourseOffering;
import com.assessment.coursewaitlist.domain.DropResult;
import com.assessment.coursewaitlist.domain.EnrollmentResult;

import java.util.Set;

public interface CourseOfferingService {

    void createOffering(String offeringId, int capacity, Set<String> requiredPrerequisites);

    /**
     * Enrolls the learner if a seat is available and they are eligible; otherwise
     * waitlists them if eligible, or reports ineligibility. See POLICY.md for the
     * eligibility rule and for how duplicate requests are handled.
     */
    EnrollmentResult enroll(String offeringId, String learnerId);

    /**
     * Drops an enrolled learner and, if a seat frees up, promotes the earliest
     * eligible waitlisted learner. See POLICY.md for the promotion/skip rule.
     */
    DropResult drop(String offeringId, String learnerId);

    CourseOffering getOffering(String offeringId);
}
