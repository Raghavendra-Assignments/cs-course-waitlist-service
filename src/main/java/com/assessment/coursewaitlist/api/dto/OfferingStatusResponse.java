package com.assessment.coursewaitlist.api.dto;

import com.assessment.coursewaitlist.domain.CourseOffering;

import java.util.List;

public record OfferingStatusResponse(String id, int capacity, List<String> enrolledLearnerIds, List<String> waitlist) {

    public static OfferingStatusResponse from(CourseOffering offering) {
        return new OfferingStatusResponse(
                offering.getId(),
                offering.getCapacity(),
                List.copyOf(offering.getEnrolledLearnerIds()),
                List.copyOf(offering.getWaitlist()));
    }
}
