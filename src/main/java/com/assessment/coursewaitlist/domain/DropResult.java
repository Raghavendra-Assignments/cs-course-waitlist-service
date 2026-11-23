package com.assessment.coursewaitlist.domain;

/**
 * @param promotedLearnerId the waitlisted learner promoted into the freed seat, if any; null otherwise.
 */
public record DropResult(DropOutcome outcome, String offeringId, String learnerId, String promotedLearnerId) {
}
