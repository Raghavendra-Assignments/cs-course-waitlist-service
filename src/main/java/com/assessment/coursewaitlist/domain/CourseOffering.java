package com.assessment.coursewaitlist.domain;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * One course offering's enrollment and waitlist state.
 *
 * Not thread-safe on its own: the service synchronizes on each offering
 * instance (intrinsic lock) around every read-then-mutate sequence so
 * enroll/drop/promote decisions stay atomic per offering. See POLICY.md.
 */
public class CourseOffering {

    private final String id;
    private final int capacity;
    private final Set<String> requiredPrerequisites;

    private final Set<String> enrolledLearnerIds = new LinkedHashSet<>();
    private final LinkedHashSet<String> waitlist = new LinkedHashSet<>();

    public CourseOffering(String id, int capacity, Set<String> requiredPrerequisites) {
        this.id = id;
        this.capacity = capacity;
        this.requiredPrerequisites = Set.copyOf(requiredPrerequisites);
    }

    public String getId() {
        return id;
    }

    public int getCapacity() {
        return capacity;
    }

    public Set<String> getRequiredPrerequisites() {
        return requiredPrerequisites;
    }

    public Set<String> getEnrolledLearnerIds() {
        return Collections.unmodifiableSet(enrolledLearnerIds);
    }

    /** Iteration order is join order (FIFO). */
    public Set<String> getWaitlist() {
        return Collections.unmodifiableSet(waitlist);
    }

    public boolean hasSeatAvailable() {
        return enrolledLearnerIds.size() < capacity;
    }

    public boolean isEnrolled(String learnerId) {
        return enrolledLearnerIds.contains(learnerId);
    }

    public boolean isWaitlisted(String learnerId) {
        return waitlist.contains(learnerId);
    }

    public void addEnrolled(String learnerId) {
        enrolledLearnerIds.add(learnerId);
    }

    public void removeEnrolled(String learnerId) {
        enrolledLearnerIds.remove(learnerId);
    }

    public void addToWaitlist(String learnerId) {
        waitlist.add(learnerId);
    }

    public void removeFromWaitlist(String learnerId) {
        waitlist.remove(learnerId);
    }
}
