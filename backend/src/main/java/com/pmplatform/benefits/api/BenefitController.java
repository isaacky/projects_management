package com.pmplatform.benefits.api;

import com.pmplatform.benefits.BenefitService;
import com.pmplatform.benefits.api.BenefitDtos.BenefitRequest;
import com.pmplatform.benefits.api.BenefitDtos.CloseRequest;
import com.pmplatform.benefits.api.BenefitDtos.FindingsRequest;
import com.pmplatform.benefits.api.BenefitDtos.MeasurementRequest;
import com.pmplatform.benefits.api.BenefitDtos.NoteRequest;
import com.pmplatform.benefits.api.BenefitDtos.PortfolioView;
import com.pmplatform.benefits.api.BenefitDtos.ProjectBenefitsView;
import com.pmplatform.benefits.api.BenefitDtos.ReviewRequest;
import com.pmplatform.benefits.api.BenefitDtos.StandardReviewsRequest;
import com.pmplatform.platform.tenant.TenantContext;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Benefits register, measurements and post-implementation reviews. */
@RestController
@RequestMapping("/api/v1")
public class BenefitController {

    private final BenefitService service;

    public BenefitController(BenefitService service) {
        this.service = service;
    }

    @GetMapping("/benefits")
    public PortfolioView portfolio() {
        return service.portfolio(TenantContext.require());
    }

    @GetMapping("/projects/{projectId}/benefits")
    public ProjectBenefitsView forProject(@PathVariable UUID projectId) {
        return service.forProject(TenantContext.require(), projectId);
    }

    @PostMapping("/projects/{projectId}/benefits")
    public ProjectBenefitsView add(@PathVariable UUID projectId, @Valid @RequestBody BenefitRequest body) {
        return service.addBenefit(TenantContext.require(), projectId, body);
    }

    @PutMapping("/projects/{projectId}/benefits/{benefitId}")
    public ProjectBenefitsView edit(@PathVariable UUID projectId, @PathVariable UUID benefitId,
            @Valid @RequestBody BenefitRequest body) {
        return service.editBenefit(TenantContext.require(), projectId, benefitId, body);
    }

    @PostMapping("/projects/{projectId}/benefits/{benefitId}/close")
    public ProjectBenefitsView close(@PathVariable UUID projectId, @PathVariable UUID benefitId,
            @Valid @RequestBody CloseRequest body) {
        return service.closeBenefit(TenantContext.require(), projectId, benefitId, body.outcome(), body.note());
    }

    @PostMapping("/projects/{projectId}/benefits/{benefitId}/reopen")
    public ProjectBenefitsView reopen(@PathVariable UUID projectId, @PathVariable UUID benefitId) {
        return service.reopenBenefit(TenantContext.require(), projectId, benefitId);
    }

    @PostMapping("/projects/{projectId}/benefits/{benefitId}/measurements")
    public ProjectBenefitsView measure(@PathVariable UUID projectId, @PathVariable UUID benefitId,
            @Valid @RequestBody MeasurementRequest body) {
        return service.recordMeasurement(TenantContext.require(), projectId, benefitId, body);
    }

    @DeleteMapping("/projects/{projectId}/benefits/{benefitId}/measurements/{measurementId}")
    public ProjectBenefitsView deleteMeasurement(@PathVariable UUID projectId, @PathVariable UUID benefitId,
            @PathVariable UUID measurementId) {
        return service.deleteMeasurement(TenantContext.require(), projectId, benefitId, measurementId);
    }

    @PostMapping("/projects/{projectId}/benefit-reviews")
    public ProjectBenefitsView planReview(@PathVariable UUID projectId, @Valid @RequestBody ReviewRequest body) {
        return service.planReview(TenantContext.require(), projectId, body);
    }

    @PostMapping("/projects/{projectId}/benefit-reviews/standard")
    public ProjectBenefitsView planStandard(@PathVariable UUID projectId,
            @Valid @RequestBody StandardReviewsRequest body) {
        return service.planStandardReviews(TenantContext.require(), projectId, body.leadId());
    }

    @PutMapping("/projects/{projectId}/benefit-reviews/{reviewId}")
    public ProjectBenefitsView editReview(@PathVariable UUID projectId, @PathVariable UUID reviewId,
            @Valid @RequestBody ReviewRequest body) {
        return service.editReview(TenantContext.require(), projectId, reviewId, body);
    }

    @PutMapping("/projects/{projectId}/benefit-reviews/{reviewId}/findings")
    public ProjectBenefitsView saveFindings(@PathVariable UUID projectId, @PathVariable UUID reviewId,
            @Valid @RequestBody FindingsRequest body) {
        return service.saveFindings(TenantContext.require(), projectId, reviewId, body, false);
    }

    @PostMapping("/projects/{projectId}/benefit-reviews/{reviewId}/complete")
    public ProjectBenefitsView complete(@PathVariable UUID projectId, @PathVariable UUID reviewId,
            @Valid @RequestBody FindingsRequest body) {
        return service.saveFindings(TenantContext.require(), projectId, reviewId, body, true);
    }

    @PostMapping("/projects/{projectId}/benefit-reviews/{reviewId}/cancel")
    public ProjectBenefitsView cancelReview(@PathVariable UUID projectId, @PathVariable UUID reviewId,
            @Valid @RequestBody NoteRequest body) {
        return service.cancelReview(TenantContext.require(), projectId, reviewId, body.note());
    }
}
