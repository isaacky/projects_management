package com.pmplatform.portfolio;

import com.pmplatform.platform.audit.AuditService;
import com.pmplatform.platform.json.JsonText;
import com.pmplatform.platform.user.CurrentUserService;
import com.pmplatform.platform.user.Roles;
import com.pmplatform.platform.user.User;
import com.pmplatform.platform.web.BusinessRuleException;
import com.pmplatform.platform.web.ForbiddenActionException;
import com.pmplatform.platform.web.NotFoundException;
import com.pmplatform.portfolio.api.ObjectiveDtos.ObjectiveInput;
import com.pmplatform.portfolio.api.ObjectiveDtos.ObjectiveRequest;
import com.pmplatform.portfolio.api.ObjectiveDtos.ObjectiveView;
import com.pmplatform.portfolio.api.ObjectiveDtos.ObjectivesView;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A project's objectives. During initiation the project manager or the PMO shapes the list freely. Once gate
 * G0 is passed the objectives are the baseline the project will be judged against: only the PMO changes them,
 * with a reason that goes on the record, and objectives are withdrawn rather than deleted.
 */
@Service
public class ProjectObjectiveService {

    private final ProjectObjectiveRepository objectives;
    private final ProjectRepository projects;
    private final List<ObjectiveAssessments> assessments;
    private final CurrentUserService currentUser;
    private final AuditService audit;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public ProjectObjectiveService(ProjectObjectiveRepository objectives, ProjectRepository projects,
            List<ObjectiveAssessments> assessments, CurrentUserService currentUser, AuditService audit,
            JdbcTemplate jdbc, Clock clock) {
        this.objectives = objectives;
        this.projects = projects;
        this.assessments = assessments;
        this.currentUser = currentUser;
        this.audit = audit;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    // ================================================================== queries

    @Transactional(readOnly = true)
    public ObjectivesView view(UUID tenantId, UUID projectId) {
        return view(project(tenantId, projectId), currentUser.require());
    }

    /** The project's objectives in number order, for the reviews. */
    @Transactional(readOnly = true)
    public List<ProjectObjective> list(UUID tenantId, UUID projectId) {
        return objectives.findByTenantIdAndProjectIdOrderByNumberAsc(tenantId, projectId);
    }

    // ================================================================== changes

    /** The objectives listed on the new-project form (called by {@link ProjectService#create}). */
    @Transactional
    public void addInitial(Project p, List<ObjectiveInput> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            return;
        }
        if (inputs.size() > ProjectObjective.MAX_PER_PROJECT) {
            throw new BusinessRuleException("A project can have at most " + ProjectObjective.MAX_PER_PROJECT
                    + " objectives");
        }
        UUID by = currentUser.current().map(User::getId).orElse(null);
        Instant now = Instant.now(clock);
        int n = 0;
        for (ObjectiveInput in : inputs) {
            if (in == null || in.statement() == null || in.statement().isBlank()) {
                continue; // an empty row left on the form
            }
            objectives.save(new ProjectObjective(p.getTenantId(), p.getId(), ++n, in.statement(),
                    in.successMeasure(), by, now));
        }
        if (n > 0) {
            audit.record(p.getTenantId(), "project.objectives_set", "Project", p.getId(),
                    JsonText.object("objectives", n));
        }
    }

    @Transactional
    public ObjectivesView add(UUID tenantId, UUID projectId, ObjectiveRequest req) {
        User me = currentUser.require();
        Project p = project(tenantId, projectId);
        String reason = requireMayChange(p, me, req.reason());
        lock(projectId);
        List<ProjectObjective> list = list(tenantId, projectId);
        if (list.size() >= ProjectObjective.MAX_PER_PROJECT) {
            throw new BusinessRuleException("A project can have at most " + ProjectObjective.MAX_PER_PROJECT
                    + " objectives");
        }
        int number = list.stream().mapToInt(ProjectObjective::getNumber).max().orElse(0) + 1;
        ProjectObjective o = new ProjectObjective(tenantId, projectId, number, req.statement(), req.successMeasure(),
                me.getId(), Instant.now(clock));
        objectives.save(o);
        audit.record(tenantId, "project.objective_added", "Project", projectId,
                JsonText.object("objective", "O" + o.getNumber(), "statement", o.getStatement(), "successMeasure",
                        o.getSuccessMeasure(), "reason", reason));
        return view(p, me);
    }

    @Transactional
    public ObjectivesView edit(UUID tenantId, UUID projectId, UUID objectiveId, ObjectiveRequest req) {
        User me = currentUser.require();
        Project p = project(tenantId, projectId);
        String reason = requireMayChange(p, me, req.reason());
        ProjectObjective o = objective(tenantId, projectId, objectiveId);
        if (req.version() != null && req.version() != o.getVersion()) {
            throw new BusinessRuleException("O" + o.getNumber() + " was changed by someone else; reload and try again");
        }
        String before = o.getStatement();
        o.word(req.statement(), req.successMeasure(), Instant.now(clock));
        audit.record(tenantId, "project.objective_changed", "Project", projectId,
                JsonText.object("objective", "O" + o.getNumber(), "from", before, "statement", o.getStatement(),
                        "successMeasure", o.getSuccessMeasure(), "reason", reason));
        return view(p, me);
    }

    @Transactional
    public ObjectivesView withdraw(UUID tenantId, UUID projectId, UUID objectiveId, String reason) {
        User me = currentUser.require();
        Project p = project(tenantId, projectId);
        requireMayChange(p, me, reason);
        ProjectObjective o = objective(tenantId, projectId, objectiveId);
        o.withdraw(reason, Instant.now(clock));
        audit.record(tenantId, "project.objective_withdrawn", "Project", projectId,
                JsonText.object("objective", "O" + o.getNumber(), "statement", o.getStatement(), "reason",
                        o.getWithdrawnReason()));
        return view(p, me);
    }

    @Transactional
    public ObjectivesView reinstate(UUID tenantId, UUID projectId, UUID objectiveId) {
        User me = currentUser.require();
        Roles.requireAny(me, java.util.Set.of(Roles.PMO_LEAD), "reinstate a withdrawn objective");
        Project p = project(tenantId, projectId);
        requireOpen(p);
        ProjectObjective o = objective(tenantId, projectId, objectiveId);
        o.reinstate(Instant.now(clock));
        audit.record(tenantId, "project.objective_reinstated", "Project", projectId,
                JsonText.object("objective", "O" + o.getNumber(), "statement", o.getStatement()));
        return view(p, me);
    }

    /** Only during initiation, and only if no review has assessed it; afterwards, withdraw it. */
    @Transactional
    public ObjectivesView delete(UUID tenantId, UUID projectId, UUID objectiveId) {
        User me = currentUser.require();
        Roles.requireAny(me, Roles.PLAN_EDITORS, "delete objectives");
        Project p = project(tenantId, projectId);
        requireOpen(p);
        if (p.getPhase() != ProjectPhase.INITIATION) {
            throw new BusinessRuleException("Objectives can only be deleted during initiation; withdraw it instead, "
                    + "with a reason");
        }
        ProjectObjective o = objective(tenantId, projectId, objectiveId);
        if (assessed(o.getId())) {
            throw new BusinessRuleException("O" + o.getNumber() + " has been assessed in a review; withdraw it instead");
        }
        objectives.delete(o);
        audit.record(tenantId, "project.objective_deleted", "Project", projectId,
                JsonText.object("objective", "O" + o.getNumber(), "statement", o.getStatement()));
        return view(p, me);
    }

    // ================================================================== rules and views

    /**
     * During initiation the project manager or the PMO may change the objectives. After gate G0 only the PMO
     * may, and must say why. Returns the trimmed reason (null during initiation if none was given).
     */
    private String requireMayChange(Project p, User me, String reason) {
        requireOpen(p);
        if (p.getPhase() == ProjectPhase.INITIATION) {
            Roles.requireAny(me, Roles.PLAN_EDITORS, "change the project's objectives");
            return ProjectObjective.optional(reason, 2000, "The reason");
        }
        if (!me.hasRole(Roles.PMO_LEAD)) {
            throw new ForbiddenActionException("The project has passed gate G0, so its objectives are baselined; "
                    + "only the PMO can change them now");
        }
        return ProjectObjective.required(reason, 2000, "After gate G0 a reason for the change");
    }

    private static void requireOpen(Project p) {
        if (p.getStatus() == ProjectStatus.CANCELLED || p.getStatus() == ProjectStatus.CLOSED) {
            throw new BusinessRuleException("Project " + p.getCode() + " is " + p.getStatus().name().toLowerCase()
                    + "; its objectives can no longer change");
        }
    }

    private ObjectivesView view(Project p, User me) {
        List<ProjectObjective> list = list(p.getTenantId(), p.getId());
        Map<UUID, ObjectiveAssessments.Latest> latest = assessments.isEmpty() ? Map.of()
                : assessments.getFirst().latest(p.getTenantId(), p.getId());
        boolean open = p.getStatus() != ProjectStatus.CANCELLED && p.getStatus() != ProjectStatus.CLOSED;
        boolean initiation = p.getPhase() == ProjectPhase.INITIATION;
        boolean editor = me.getRoles().stream().anyMatch(Roles.PLAN_EDITORS::contains);
        boolean pmo = me.hasRole(Roles.PMO_LEAD);
        boolean mayChange = open && (initiation ? editor : pmo);
        List<ObjectiveView> views = list.stream().map(o -> {
            boolean active = o.getStatus() == ObjectiveStatus.ACTIVE;
            ObjectiveAssessments.Latest l = latest.get(o.getId());
            return new ObjectiveView(o.getId(), o.getNumber(), o.getStatement(), o.getSuccessMeasure(), o.getStatus(),
                    o.getWithdrawnReason(), l == null ? null : l.outcome(), l == null ? null : l.evidence(),
                    l == null ? null : l.reviewTitle(), l == null ? null : l.conductedOn(), mayChange && active,
                    mayChange && active, open && pmo && !active, open && initiation && editor && !assessed(o.getId()),
                    o.getVersion());
        }).toList();
        return new ObjectivesView(p.getId(), views,
                mayChange && list.size() < ProjectObjective.MAX_PER_PROJECT, !initiation);
    }

    private boolean assessed(UUID objectiveId) {
        return !assessments.isEmpty() && assessments.getFirst().isAssessed(objectiveId);
    }

    private Project project(UUID tenantId, UUID projectId) {
        return projects.findByTenantIdAndId(tenantId, projectId)
                .orElseThrow(() -> new NotFoundException("Project " + projectId + " not found"));
    }

    private ProjectObjective objective(UUID tenantId, UUID projectId, UUID id) {
        return objectives.findByTenantIdAndId(tenantId, id).filter(o -> o.getProjectId().equals(projectId))
                .orElseThrow(() -> new NotFoundException("Objective not found"));
    }

    /** Serialises numbering of a project's objectives. */
    private void lock(UUID projectId) {
        jdbc.queryForList("SELECT pg_advisory_xact_lock(?)",
                (projectId.getMostSignificantBits() ^ projectId.getLeastSignificantBits()) * 31 + 0x4F424A45L);
    }
}
