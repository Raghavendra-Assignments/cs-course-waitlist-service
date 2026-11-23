package com.assessment.coursewaitlist.store;

import com.assessment.coursewaitlist.domain.CourseOffering;
import com.assessment.coursewaitlist.exception.OfferingNotFoundException;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory offering store for this bounded exercise; no persistence layer required. */
@Component
public class CourseOfferingStore {

    private final Map<String, CourseOffering> offerings = new ConcurrentHashMap<>();

    public void save(CourseOffering offering) {
        offerings.put(offering.getId(), offering);
    }

    public CourseOffering getOrThrow(String offeringId) {
        CourseOffering offering = offerings.get(offeringId);
        if (offering == null) {
            throw new OfferingNotFoundException(offeringId);
        }
        return offering;
    }
}
