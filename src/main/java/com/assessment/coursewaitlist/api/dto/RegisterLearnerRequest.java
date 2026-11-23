package com.assessment.coursewaitlist.api.dto;

import java.util.Set;

public record RegisterLearnerRequest(String id, Set<String> completedPrerequisites) {
}
