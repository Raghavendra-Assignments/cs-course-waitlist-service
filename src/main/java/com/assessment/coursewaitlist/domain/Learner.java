package com.assessment.coursewaitlist.domain;

import java.util.Set;

public class Learner {

    private final String id;
    private final Set<String> completedPrerequisites;

    public Learner(String id, Set<String> completedPrerequisites) {
        this.id = id;
        this.completedPrerequisites = Set.copyOf(completedPrerequisites);
    }

    public String getId() {
        return id;
    }

    public Set<String> getCompletedPrerequisites() {
        return completedPrerequisites;
    }
}
