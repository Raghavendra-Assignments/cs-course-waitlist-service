package com.assessment.coursewaitlist.api.dto;

import java.util.Set;

public record CreateOfferingRequest(String id, int capacity, Set<String> requiredPrerequisites) {
}
