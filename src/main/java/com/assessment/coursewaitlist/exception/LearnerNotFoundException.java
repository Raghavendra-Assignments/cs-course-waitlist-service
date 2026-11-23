package com.assessment.coursewaitlist.exception;

public class LearnerNotFoundException extends RuntimeException {

    public LearnerNotFoundException(String learnerId) {
        super("No learner with id '%s'".formatted(learnerId));
    }
}
