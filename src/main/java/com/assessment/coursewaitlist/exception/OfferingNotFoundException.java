package com.assessment.coursewaitlist.exception;

public class OfferingNotFoundException extends RuntimeException {

    public OfferingNotFoundException(String offeringId) {
        super("No course offering with id '%s'".formatted(offeringId));
    }
}
