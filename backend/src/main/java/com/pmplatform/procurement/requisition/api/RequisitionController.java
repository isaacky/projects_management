package com.pmplatform.procurement.requisition.api;

import com.pmplatform.platform.audit.AuditService;
import com.pmplatform.platform.tenant.TenantContext;
import com.pmplatform.portfolio.api.AuditEntryResponse;
import com.pmplatform.procurement.requisition.RequisitionService;
import com.pmplatform.procurement.requisition.RequisitionService.Scope;
import com.pmplatform.procurement.requisition.api.RequisitionDtos.DecisionRequest;
import com.pmplatform.procurement.requisition.api.RequisitionDtos.RequisitionDetail;
import com.pmplatform.procurement.requisition.api.RequisitionDtos.RequisitionRequest;
import com.pmplatform.procurement.requisition.api.RequisitionDtos.RequisitionSummary;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Purchase requisitions. {@code scope}: MINE (default), AWAITING_ME (my approvals) or ALL.
 */
@RestController
@RequestMapping("/api/v1/requisitions")
public class RequisitionController {

    private final RequisitionService service;
    private final AuditService audit;

    public RequisitionController(RequisitionService service, AuditService audit) {
        this.service = service;
        this.audit = audit;
    }

    @GetMapping
    public List<RequisitionSummary> list(@RequestParam(defaultValue = "MINE") Scope scope,
            @RequestParam(required = false) UUID projectId) {
        return service.list(TenantContext.require(), scope, projectId);
    }

    @GetMapping("/{id}")
    public RequisitionDetail get(@PathVariable UUID id) {
        return service.get(TenantContext.require(), id);
    }

    @PostMapping
    public ResponseEntity<RequisitionDetail> create(@Valid @RequestBody RequisitionRequest body) {
        RequisitionDetail created = service.create(TenantContext.require(), body);
        return ResponseEntity.created(URI.create("/api/v1/requisitions/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public RequisitionDetail update(@PathVariable UUID id, @Valid @RequestBody RequisitionRequest body) {
        return service.update(TenantContext.require(), id, body);
    }

    @PostMapping("/{id}/submit")
    public RequisitionDetail submit(@PathVariable UUID id) {
        return service.submit(TenantContext.require(), id);
    }

    @PostMapping("/{id}/approve")
    public RequisitionDetail approve(@PathVariable UUID id, @Valid @RequestBody(required = false) DecisionRequest body) {
        return service.approve(TenantContext.require(), id, body == null ? null : body.comment());
    }

    @PostMapping("/{id}/reject")
    public RequisitionDetail reject(@PathVariable UUID id, @Valid @RequestBody DecisionRequest body) {
        return service.reject(TenantContext.require(), id, body.comment());
    }

    @PostMapping("/{id}/return")
    public RequisitionDetail returnForChanges(@PathVariable UUID id, @Valid @RequestBody DecisionRequest body) {
        return service.returnForChanges(TenantContext.require(), id, body.comment());
    }

    @PostMapping("/{id}/cancel")
    public RequisitionDetail cancel(@PathVariable UUID id, @Valid @RequestBody(required = false) DecisionRequest body) {
        return service.cancel(TenantContext.require(), id, body == null ? null : body.comment());
    }

    @GetMapping("/{id}/history")
    public List<AuditEntryResponse> history(@PathVariable UUID id) {
        UUID tenant = TenantContext.require();
        service.get(tenant, id); // 404 if not in this tenant
        return audit.history(tenant, id).stream().map(AuditEntryResponse::from).toList();
    }
}
