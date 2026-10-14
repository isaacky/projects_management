package com.pmplatform.procurement.evaluation.api;

import com.pmplatform.platform.tenant.TenantContext;
import com.pmplatform.procurement.evaluation.EvaluationService;
import com.pmplatform.procurement.evaluation.api.EvaluationDtos.CommitteeChangeRequest;
import com.pmplatform.procurement.evaluation.api.EvaluationDtos.CommitteeRequest;
import com.pmplatform.procurement.evaluation.api.EvaluationDtos.CriteriaRequest;
import com.pmplatform.procurement.evaluation.api.EvaluationDtos.DecisionRequest;
import com.pmplatform.procurement.evaluation.api.EvaluationDtos.DeclarationRequest;
import com.pmplatform.procurement.evaluation.api.EvaluationDtos.EvaluationResponse;
import com.pmplatform.procurement.evaluation.api.EvaluationDtos.FinancialRequest;
import com.pmplatform.procurement.evaluation.api.EvaluationDtos.MarksRequest;
import com.pmplatform.procurement.evaluation.api.EvaluationDtos.ReasonRequest;
import com.pmplatform.procurement.evaluation.api.EvaluationDtos.ReportRequest;
import com.pmplatform.procurement.evaluation.api.EvaluationDtos.SetUpRequest;
import com.pmplatform.procurement.evaluation.api.EvaluationDtos.SignRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Evaluation of a tender's bids, from setup to the approved award. */
@RestController
@RequestMapping("/api/v1/tenders/{tenderId}/evaluation")
public class EvaluationController {

    private final EvaluationService service;

    public EvaluationController(EvaluationService service) {
        this.service = service;
    }

    @GetMapping
    public EvaluationResponse get(@PathVariable UUID tenderId) {
        return service.get(TenantContext.require(), tenderId);
    }

    // ---- setup (procurement) and appointment (accounting officer)

    @PostMapping
    public EvaluationResponse setUp(@PathVariable UUID tenderId, @Valid @RequestBody SetUpRequest body) {
        return service.setUp(TenantContext.require(), tenderId, body.templateCode());
    }

    @PostMapping("/template")
    public EvaluationResponse loadTemplate(@PathVariable UUID tenderId, @Valid @RequestBody SetUpRequest body) {
        return service.loadTemplate(TenantContext.require(), tenderId, body.templateCode());
    }

    @PutMapping("/criteria")
    public EvaluationResponse criteria(@PathVariable UUID tenderId, @Valid @RequestBody CriteriaRequest body) {
        return service.saveCriteria(TenantContext.require(), tenderId, body);
    }

    @PutMapping("/committee")
    public EvaluationResponse committee(@PathVariable UUID tenderId, @Valid @RequestBody CommitteeRequest body) {
        return service.saveCommittee(TenantContext.require(), tenderId, body);
    }

    @PostMapping("/appoint")
    public EvaluationResponse appoint(@PathVariable UUID tenderId) {
        return service.appoint(TenantContext.require(), tenderId);
    }

    @PostMapping("/committee-changes")
    public EvaluationResponse changeCommittee(@PathVariable UUID tenderId,
            @Valid @RequestBody CommitteeChangeRequest body) {
        return service.changeCommittee(TenantContext.require(), tenderId, body);
    }

    // ---- members

    @PostMapping("/declaration")
    public EvaluationResponse declare(@PathVariable UUID tenderId, @Valid @RequestBody DeclarationRequest body) {
        return service.declare(TenantContext.require(), tenderId, Boolean.TRUE.equals(body.conflict()),
                body.declaration());
    }

    @PutMapping("/marks")
    public EvaluationResponse marks(@PathVariable UUID tenderId, @Valid @RequestBody MarksRequest body) {
        return service.saveMarks(TenantContext.require(), tenderId, body);
    }

    // ---- chair

    @PostMapping("/close-stage")
    public EvaluationResponse closeStage(@PathVariable UUID tenderId) {
        return service.closeStage(TenantContext.require(), tenderId);
    }

    @PutMapping("/bids/{bidId}/price")
    public EvaluationResponse price(@PathVariable UUID tenderId, @PathVariable UUID bidId,
            @Valid @RequestBody FinancialRequest body) {
        return service.saveFinancial(TenantContext.require(), tenderId, bidId, body.evaluatedPriceMinor(), body.note());
    }

    @PostMapping("/bids/{bidId}/reject")
    public EvaluationResponse reject(@PathVariable UUID tenderId, @PathVariable UUID bidId,
            @Valid @RequestBody ReasonRequest body) {
        return service.rejectAtFinancial(TenantContext.require(), tenderId, bidId, body.reason());
    }

    @PostMapping("/report")
    public EvaluationResponse report(@PathVariable UUID tenderId, @Valid @RequestBody ReportRequest body) {
        return service.submitReport(TenantContext.require(), tenderId, body);
    }

    // ---- signatures and approval

    @PostMapping("/sign")
    public EvaluationResponse sign(@PathVariable UUID tenderId, @Valid @RequestBody SignRequest body) {
        return service.sign(TenantContext.require(), tenderId, body.note());
    }

    @PostMapping("/decision")
    public EvaluationResponse decide(@PathVariable UUID tenderId, @Valid @RequestBody DecisionRequest body) {
        return service.decide(TenantContext.require(), tenderId, body);
    }
}
