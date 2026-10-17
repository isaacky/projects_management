package com.pmplatform.portfolio.api;

import com.pmplatform.platform.tenant.TenantContext;
import com.pmplatform.portfolio.ProjectObjectiveService;
import com.pmplatform.portfolio.api.ObjectiveDtos.ObjectiveRequest;
import com.pmplatform.portfolio.api.ObjectiveDtos.ObjectivesView;
import com.pmplatform.portfolio.api.ObjectiveDtos.WithdrawRequest;
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

@RestController
@RequestMapping("/api/v1/projects/{projectId}/objectives")
public class ObjectiveController {

    private final ProjectObjectiveService service;

    public ObjectiveController(ProjectObjectiveService service) {
        this.service = service;
    }

    @GetMapping
    public ObjectivesView list(@PathVariable UUID projectId) {
        return service.view(TenantContext.require(), projectId);
    }

    @PostMapping
    public ObjectivesView add(@PathVariable UUID projectId, @Valid @RequestBody ObjectiveRequest body) {
        return service.add(TenantContext.require(), projectId, body);
    }

    @PutMapping("/{objectiveId}")
    public ObjectivesView edit(@PathVariable UUID projectId, @PathVariable UUID objectiveId,
            @Valid @RequestBody ObjectiveRequest body) {
        return service.edit(TenantContext.require(), projectId, objectiveId, body);
    }

    @PostMapping("/{objectiveId}/withdraw")
    public ObjectivesView withdraw(@PathVariable UUID projectId, @PathVariable UUID objectiveId,
            @Valid @RequestBody WithdrawRequest body) {
        return service.withdraw(TenantContext.require(), projectId, objectiveId, body.reason());
    }

    @PostMapping("/{objectiveId}/reinstate")
    public ObjectivesView reinstate(@PathVariable UUID projectId, @PathVariable UUID objectiveId) {
        return service.reinstate(TenantContext.require(), projectId, objectiveId);
    }

    @DeleteMapping("/{objectiveId}")
    public ObjectivesView delete(@PathVariable UUID projectId, @PathVariable UUID objectiveId) {
        return service.delete(TenantContext.require(), projectId, objectiveId);
    }
}
