package com.pmplatform.portfolio.api;

import com.pmplatform.platform.audit.AuditService;
import com.pmplatform.platform.tenant.TenantContext;
import com.pmplatform.portfolio.Project;
import com.pmplatform.portfolio.ProjectPhase;
import com.pmplatform.portfolio.ProjectService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final ProjectService service;
    private final AuditService audit;

    public ProjectController(ProjectService service, AuditService audit) {
        this.service = service;
        this.audit = audit;
    }

    @GetMapping
    public List<ProjectResponse> list(@RequestParam(required = false) ProjectPhase phase) {
        return service.list(TenantContext.require(), phase).stream()
                .map(ProjectResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public ProjectResponse get(@PathVariable UUID id) {
        return ProjectResponse.from(service.get(TenantContext.require(), id));
    }

    @PostMapping
    public ResponseEntity<ProjectResponse> create(@Valid @RequestBody CreateProjectRequest request) {
        Project project = service.create(TenantContext.require(), request);
        return ResponseEntity.created(URI.create("/api/v1/projects/" + project.getId()))
                .body(ProjectResponse.from(project));
    }

    /** Records a gate decision and moves the project into its next phase. */
    @PostMapping("/{id}/advance")
    public ProjectResponse advance(@PathVariable UUID id,
            @Valid @RequestBody AdvancePhaseRequest request) {
        return ProjectResponse.from(
                service.advancePhase(TenantContext.require(), id, request.decisionNote()));
    }

    @GetMapping("/{id}/history")
    public List<AuditEntryResponse> history(@PathVariable UUID id) {
        UUID tenant = TenantContext.require();
        service.get(tenant, id); // 404 if the project is not in this tenant
        return audit.history(tenant, id).stream().map(AuditEntryResponse::from).toList();
    }
}
