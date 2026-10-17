package com.pmplatform.portfolio;

import com.pmplatform.platform.audit.AuditService;
import com.pmplatform.platform.json.JsonText;
import com.pmplatform.platform.web.BusinessRuleException;
import com.pmplatform.platform.web.NotFoundException;
import com.pmplatform.portfolio.api.CreateProjectRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {

    private final ProjectRepository projects;
    private final AuditService audit;
    private final Clock clock;
    private final List<PhaseGateCheck> gateChecks;
    private final ProjectObjectiveService objectives;

    public ProjectService(ProjectRepository projects, AuditService audit, Clock clock, List<PhaseGateCheck> gateChecks,
            ProjectObjectiveService objectives) {
        this.projects = projects;
        this.objectives = objectives;
        this.audit = audit;
        this.clock = clock;
        this.gateChecks = gateChecks;
    }

    @Transactional(readOnly = true)
    public List<Project> list(UUID tenantId, ProjectPhase phase) {
        return phase == null
                ? projects.findByTenantIdOrderByCodeAsc(tenantId)
                : projects.findByTenantIdAndPhaseOrderByCodeAsc(tenantId, phase);
    }

    @Transactional(readOnly = true)
    public Project get(UUID tenantId, UUID id) {
        return projects.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new NotFoundException("Project " + id + " not found"));
    }

    /** Projects by id within the tenant, for other contexts that show project names. */
    @Transactional(readOnly = true)
    public java.util.Map<UUID, Project> byIds(UUID tenantId, java.util.Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return java.util.Map.of();
        }
        return projects.findByTenantIdAndIdIn(tenantId, ids).stream()
                .collect(java.util.stream.Collectors.toMap(Project::getId, p -> p));
    }

    @Transactional
    public Project create(UUID tenantId, CreateProjectRequest req) {
        String code = req.code().trim().toUpperCase(Locale.ROOT);
        if (projects.existsByTenantIdAndCode(tenantId, code)) {
            throw new BusinessRuleException("A project with code " + code + " already exists");
        }
        Project project = Project.create(tenantId, code, req.name().trim(), req.description(),
                req.sponsor(), req.budgetMinor(), req.currencyOrDefault(), Instant.now(clock));
        projects.save(project);
        audit.record(tenantId, "project.created", "Project", project.getId(),
                JsonText.object("code", code));
        objectives.addInitial(project, req.objectives());
        return project;
    }

    @Transactional
    public Project advancePhase(UUID tenantId, UUID id, String decisionNote) {
        Project project = get(tenantId, id);
        ProjectPhase from = project.getPhase();
        List<String> blockers = gateChecks.stream().flatMap(c -> c.blockers(tenantId, project).stream()).toList();
        if (!blockers.isEmpty()) {
            throw new BusinessRuleException("Gate " + from.exitGate().orElse("") + " cannot be passed yet: "
                    + String.join("; ", blockers));
        }
        String gate = project.advancePhase(Instant.now(clock));
        audit.record(tenantId, "project.gate.passed", "Project", id,
                JsonText.object("gate", gate, "from", from, "to", project.getPhase(),
                        "note", decisionNote));
        return project;
    }

    /** Closes a project in implementation once closeout is complete (called by the closeout context). */
    @Transactional
    public Project close(UUID tenantId, UUID id, String note) {
        Project project = get(tenantId, id);
        project.close(Instant.now(clock));
        audit.record(tenantId, "project.closed", "Project", id, JsonText.object("note", note));
        return project;
    }
}
