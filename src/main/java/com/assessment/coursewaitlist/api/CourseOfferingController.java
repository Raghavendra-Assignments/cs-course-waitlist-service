package com.assessment.coursewaitlist.api;

import com.assessment.coursewaitlist.api.dto.CreateOfferingRequest;
import com.assessment.coursewaitlist.api.dto.OfferingStatusResponse;
import com.assessment.coursewaitlist.api.dto.RegisterLearnerRequest;
import com.assessment.coursewaitlist.domain.CourseOffering;
import com.assessment.coursewaitlist.domain.DropResult;
import com.assessment.coursewaitlist.domain.EnrollmentResult;
import com.assessment.coursewaitlist.domain.Learner;
import com.assessment.coursewaitlist.service.CourseOfferingService;
import com.assessment.coursewaitlist.store.LearnerStore;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
public class CourseOfferingController {

    private final CourseOfferingService offeringService;
    private final LearnerStore learnerStore;

    public CourseOfferingController(CourseOfferingService offeringService, LearnerStore learnerStore) {
        this.offeringService = offeringService;
        this.learnerStore = learnerStore;
    }

    // --- setup endpoints (not part of the acceptance criteria; needed since
    // there is no supplied catalog/learner-record system in this exercise) ---

    @PostMapping("/offerings")
    @ResponseStatus(HttpStatus.CREATED)
    public void createOffering(@RequestBody CreateOfferingRequest request) {
        offeringService.createOffering(request.id(), request.capacity(), request.requiredPrerequisites());
    }

    @PostMapping("/learners")
    @ResponseStatus(HttpStatus.CREATED)
    public void registerLearner(@RequestBody RegisterLearnerRequest request) {
        learnerStore.save(new Learner(request.id(), request.completedPrerequisites()));
    }

    @GetMapping("/offerings/{offeringId}")
    public OfferingStatusResponse getOffering(@PathVariable String offeringId) {
        CourseOffering offering = offeringService.getOffering(offeringId);
        // Same lock enroll()/drop() hold while mutating: without this, a concurrent
        // mutation can race with the List.copyOf() iteration below and throw
        // ConcurrentModificationException, since getEnrolledLearnerIds()/getWaitlist()
        // return a live view, not a snapshot.
        synchronized (offering) {
            return OfferingStatusResponse.from(offering);
        }
    }

    // --- scoped feature endpoints ---

    @PostMapping("/offerings/{offeringId}/learners/{learnerId}/enroll")
    public EnrollmentResult enroll(@PathVariable String offeringId, @PathVariable String learnerId) {
        return offeringService.enroll(offeringId, learnerId);
    }

    @PostMapping("/offerings/{offeringId}/learners/{learnerId}/drop")
    public DropResult drop(@PathVariable String offeringId, @PathVariable String learnerId) {
        return offeringService.drop(offeringId, learnerId);
    }
}
