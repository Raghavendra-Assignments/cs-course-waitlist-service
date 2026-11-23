package com.assessment.coursewaitlist.store;

import com.assessment.coursewaitlist.domain.Learner;
import com.assessment.coursewaitlist.exception.LearnerNotFoundException;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory learner store for this bounded exercise; no persistence layer required. */
@Component
public class LearnerStore {

    private final Map<String, Learner> learners = new ConcurrentHashMap<>();

    public void save(Learner learner) {
        learners.put(learner.getId(), learner);
    }

    public Learner getOrThrow(String learnerId) {
        Learner learner = learners.get(learnerId);
        if (learner == null) {
            throw new LearnerNotFoundException(learnerId);
        }
        return learner;
    }
}
