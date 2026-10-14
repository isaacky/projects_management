package com.pmplatform.procurement.requisition;

import com.pmplatform.platform.audit.AuditService;
import com.pmplatform.platform.json.JsonText;
import com.pmplatform.platform.notification.NotificationService;
import com.pmplatform.platform.notification.NotificationService.Message;
import com.pmplatform.platform.user.CurrentUserService;
import com.pmplatform.platform.user.Roles;
import com.pmplatform.platform.user.User;
import com.pmplatform.platform.web.BusinessRuleException;
import com.pmplatform.platform.web.NotFoundException;
import com.pmplatform.portfolio.Project;
import com.pmplatform.portfolio.ProjectService;
import com.pmplatform.portfolio.ProjectStatus;
import com.pmplatform.procurement.config.ApprovalStage;
import com.pmplatform.procurement.config.ProcurementMethodRepository;
import com.pmplatform.procurement.config.ProcurementSettingsService;
import com.pmplatform.procurement.config.api.SettingsDtos.CheckResponse;
import com.pmplatform.procurement.plan.PlanCoverage;
import com.pmplatform.procurement.plan.ProcurementPlanService;
import com.pmplatform.procurement.requisition.Requisition.ChainStep;
import com.pmplatform.procurement.requisition.Requisition.Decision;
import com.pmplatform.procurement.requisition.Requisition.LineInput;
import com.pmplatform.procurement.requisition.api.RequisitionDtos.ApprovalResponse;
import com.pmplatform.procurement.requisition.api.RequisitionDtos.LineResponse;
import com.pmplatform.procurement.requisition.api.RequisitionDtos.Permissions;
import com.pmplatform.procurement.requisition.api.RequisitionDtos.PersonRef;
import com.pmplatform.procurement.requisition.api.RequisitionDtos.ProjectRef;
import com.pmplatform.procurement.requisition.api.RequisitionDtos.RequisitionDetail;
import com.pmplatform.procurement.requisition.api.RequisitionDtos.RequisitionRequest;
import com.pmplatform.procurement.requisition.api.RequisitionDtos.RequisitionSummary;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Requisition use cases. The approval chain comes from the tenant's procurement
 * settings (REQUISITION thresholds) at submission time; every step change is audited
 * and the people who must act next get an in-app notification.
 */
@Service
public class RequisitionService {

    public enum Scope { MINE, AWAITING_ME, ALL }

    private static final int LIST_LIMIT = 200;

    private final RequisitionRepository requisitions;
    private final RequisitionNumbers numbers;
    private final ProjectService projects;
    private final ProcurementSettingsService settings;
    private final ProcurementMethodRepository methods;
    private final ProcurementPlanService plan;
    private final CurrentUserService currentUser;
    private final NotificationService notifications;
    private final AuditService audit;
    private final Clock clock;

    public RequisitionService(RequisitionRepository requisitions, RequisitionNumbers numbers,
            ProjectService projects, ProcurementSettingsService settings, ProcurementMethodRepository methods,
            ProcurementPlanService plan, CurrentUserService currentUser, NotificationService notifications,
            AuditService audit, Clock clock) {
        this.requisitions = requisitions;
        this.numbers = numbers;
        this.projects = projects;
        this.settings = settings;
        this.methods = methods;
        this.plan = plan;
        this.currentUser = currentUser;
        this.notifications = notifications;
        this.audit = audit;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ queries

    @Transactional(readOnly = true)
    public List<RequisitionSummary> list(UUID tenantId, Scope scope, UUID projectId) {
        User me = currentUser.require();
        PageRequest page = PageRequest.of(0, LIST_LIMIT);
        List<Requisition> rows;
        if (projectId != null) {
            rows = requisitions.findByTenantIdAndProjectIdOrderByCreatedAtDesc(tenantId, projectId, page);
        } else {
            rows = switch (scope) {
                case MINE -> requisitions.findByTenantIdAndRequestedByOrderByCreatedAtDesc(tenantId, me.getId(), page);
                case AWAITING_ME -> me.getRoles().isEmpty() ? List.of()
                        : requisitions.findAwaiting(tenantId, me.getRoles(), me.getId(), page);
                case ALL -> requisitions.findByTenantIdOrderByCreatedAtDesc(tenantId, page);
            };
        }
        Map<UUID, Project> projectMap = projects.byIds(tenantId,
                rows.stream().map(Requisition::getProjectId).collect(Collectors.toSet()));
        Map<UUID, User> people = people(tenantId, rows.stream().map(Requisition::getRequestedBy));
        return rows.stream().map(r -> new RequisitionSummary(r.getId(), r.getNumber(), r.getTitle(),
                projectRef(projectMap.get(r.getProjectId()), r.getProjectId()), r.getStatus(),
                r.getEstimatedTotalMinor(), r.getCurrency(), person(people, r.getRequestedBy()),
                r.pendingStep().map(RequisitionApproval::getApproverRole).orElse(null),
                r.getCreatedAt(), r.getSubmittedAt())).toList();
    }

    @Transactional(readOnly = true)
    public RequisitionDetail get(UUID tenantId, UUID id) {
        return detail(load(tenantId, id), currentUser.require());
    }

    // ------------------------------------------------------------------ commands

    @Transactional
    public RequisitionDetail create(UUID tenantId, RequisitionRequest req) {
        User me = currentUser.require();
        String planDepartment = validate(tenantId, req);
        Instant now = Instant.now(clock);
        Requisition r = new Requisition(tenantId, numbers.next(tenantId), me.getId(), now);
        apply(r, me, req, planDepartment, now);
        requisitions.save(r);
        audit.record(tenantId, "requisition.created", "Requisition", r.getId(),
                JsonText.object("number", r.getNumber(), "projectId", r.getProjectId(),
                        "estimatedTotalMinor", r.getEstimatedTotalMinor(), "currency", r.getCurrency()));
        return detail(r, me);
    }

    @Transactional
    public RequisitionDetail update(UUID tenantId, UUID id, RequisitionRequest req) {
        User me = currentUser.require();
        Requisition r = load(tenantId, id);
        String planDepartment = validate(tenantId, req);
        apply(r, me, req, planDepartment, Instant.now(clock));
        audit.record(tenantId, "requisition.updated", "Requisition", r.getId(),
                JsonText.object("number", r.getNumber(), "estimatedTotalMinor", r.getEstimatedTotalMinor(),
                        "lines", r.getLines().size()));
        return detail(r, me);
    }

    @Transactional
    public RequisitionDetail submit(UUID tenantId, UUID id) {
        User me = currentUser.require();
        Requisition r = load(tenantId, id);

        CheckResponse check = settings.check(tenantId, ApprovalStage.REQUISITION, r.getProposedMethodCode(),
                r.getEstimatedTotalMinor(), r.getCurrency());
        if (r.getProposedMethodCode() != null && !check.methodAllowed()) {
            throw new BusinessRuleException(r.getProposedMethodCode()
                    + " cannot be used for this amount. Allowed: " + String.join(", ", check.allowedMethods()));
        }
        List<ChainStep> chain = new ArrayList<>(check.approvers().stream()
                .map(s -> new ChainStep(s.step(), s.approverRole())).toList());
        PlanCoverage coverage = plan.checkRequisition(tenantId, r.getId(), r.getPlanLineId(),
                r.getUnplannedReason(), r.getEstimatedTotalMinor(), r.getCurrency());
        if (coverage == PlanCoverage.UNPLANNED
                && chain.stream().noneMatch(s -> s.approverRole().equals(Roles.ACCOUNTING_OFFICER))) {
            // Purchases outside the approved plan also need the accounting officer.
            int last = chain.stream().mapToInt(ChainStep::stepOrder).max().orElse(0);
            chain.add(new ChainStep(last + 1, Roles.ACCOUNTING_OFFICER));
        }
        for (ChainStep step : chain) {
            boolean someoneElse = currentUser.withRole(tenantId, step.approverRole()).stream()
                    .anyMatch(u -> !u.getId().equals(me.getId()));
            if (!someoneElse) {
                throw new BusinessRuleException("Nobody else holds the " + step.approverRole()
                        + " role needed for approval step " + step.stepOrder()
                        + ". Ask an administrator to assign it.");
            }
        }

        RequisitionApproval first = r.submit(me.getId(), chain, Instant.now(clock));
        r.planChecked(coverage);
        audit.record(tenantId, "requisition.submitted", "Requisition", r.getId(),
                JsonText.object("number", r.getNumber(), "round", r.getCurrentRound(), "plan", coverage,
                        "planLineId", r.getPlanLineId(), "unplannedReason", r.getUnplannedReason(),
                        "chain", chain.stream().map(ChainStep::approverRole).collect(Collectors.joining(" > "))));
        notifyApprovers(tenantId, r, first, me);
        return detail(r, me);
    }

    @Transactional
    public RequisitionDetail approve(UUID tenantId, UUID id, String comment) {
        User me = currentUser.require();
        Requisition r = load(tenantId, id);
        Decision d = r.approve(me.getId(), me.getRoles(), comment, Instant.now(clock));
        auditDecision(tenantId, r, d, "requisition.approved_step");
        if (d.nextStep().isPresent()) {
            notifyApprovers(tenantId, r, d.nextStep().get(), me);
        } else {
            notifications.notifyUsers(tenantId, List.of(r.getRequestedBy()), new Message(
                    "requisition.approved", r.getNumber() + " approved",
                    "\"" + r.getTitle() + "\" passed every approval step and can go to procurement.",
                    link(r)));
        }
        return detail(r, me);
    }

    @Transactional
    public RequisitionDetail reject(UUID tenantId, UUID id, String comment) {
        User me = currentUser.require();
        Requisition r = load(tenantId, id);
        Decision d = r.reject(me.getId(), me.getRoles(), comment, Instant.now(clock));
        auditDecision(tenantId, r, d, "requisition.rejected");
        notifications.notifyUsers(tenantId, List.of(r.getRequestedBy()), new Message(
                "requisition.rejected", r.getNumber() + " rejected",
                me.getDisplayName() + ": " + d.step().getComment(), link(r)));
        return detail(r, me);
    }

    @Transactional
    public RequisitionDetail returnForChanges(UUID tenantId, UUID id, String comment) {
        User me = currentUser.require();
        Requisition r = load(tenantId, id);
        Decision d = r.returnForChanges(me.getId(), me.getRoles(), comment, Instant.now(clock));
        auditDecision(tenantId, r, d, "requisition.returned");
        notifications.notifyUsers(tenantId, List.of(r.getRequestedBy()), new Message(
                "requisition.returned", r.getNumber() + " returned for changes",
                me.getDisplayName() + ": " + d.step().getComment(), link(r)));
        return detail(r, me);
    }

    @Transactional
    public RequisitionDetail cancel(UUID tenantId, UUID id, String comment) {
        User me = currentUser.require();
        Requisition r = load(tenantId, id);
        Optional<RequisitionApproval> wasPending = r.cancel(me.getId(), Instant.now(clock));
        audit.record(tenantId, "requisition.cancelled", "Requisition", r.getId(),
                JsonText.object("number", r.getNumber(), "comment", comment));
        wasPending.ifPresent(step -> notifications.notifyRole(tenantId, step.getApproverRole(), new Message(
                "requisition.withdrawn", r.getNumber() + " withdrawn",
                me.getDisplayName() + " withdrew \"" + r.getTitle() + "\". No action needed.", link(r))));
        return detail(r, me);
    }

    // ------------------------------------------------------------------ helpers

    private Requisition load(UUID tenantId, UUID id) {
        return requisitions.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new NotFoundException("Requisition " + id + " not found"));
    }

    /** Returns the plan line's department when a line is named. */
    private String validate(UUID tenantId, RequisitionRequest req) {
        Project project = projects.get(tenantId, req.projectId());
        if (project.getStatus() != ProjectStatus.ACTIVE) {
            throw new BusinessRuleException("Project " + project.getCode() + " is " + project.getStatus()
                    + "; requisitions can only be raised on active projects");
        }
        if (req.proposedMethodCode() != null
                && methods.findByTenantIdAndCode(tenantId, req.proposedMethodCode()).isEmpty()) {
            throw new BusinessRuleException("Unknown procurement method " + req.proposedMethodCode());
        }
        return req.planLineId() == null ? null : plan.checkLink(tenantId, req.planLineId(), req.currencyOrDefault());
    }

    private static void apply(Requisition r, User me, RequisitionRequest req, String planDepartment, Instant now) {
        List<LineInput> lines = req.lines().stream()
                .map(l -> new LineInput(l.description().trim(), l.quantity(), l.unit().trim(), l.unitPriceMinor()))
                .toList();
        r.edit(me.getId(), req.projectId(), req.title().trim(), blank(req.justification()),
                blank(req.department()) != null ? blank(req.department()) : planDepartment, req.neededBy(), req.currencyOrDefault(), blank(req.proposedMethodCode()),
                lines, now);
        r.linkPlan(req.planLineId(), req.unplannedReason(), now);
    }

    private void notifyApprovers(UUID tenantId, Requisition r, RequisitionApproval step, User actor) {
        String body = actor.getDisplayName() + " · " + r.getTitle() + " · approval step " + step.getStepOrder();
        notifications.notifyUsers(tenantId,
                currentUser.withRole(tenantId, step.getApproverRole()).stream()
                        .map(User::getId)
                        .filter(id -> !id.equals(r.getRequestedBy()))
                        .toList(),
                new Message("requisition.awaiting_approval", r.getNumber() + " needs your approval", body, link(r)));
    }

    private void auditDecision(UUID tenantId, Requisition r, Decision d, String action) {
        audit.record(tenantId, action, "Requisition", r.getId(),
                JsonText.object("number", r.getNumber(), "round", d.step().getRound(),
                        "step", d.step().getStepOrder(), "role", d.step().getApproverRole(),
                        "result", d.resultingStatus(), "comment", d.step().getComment()));
    }

    private RequisitionDetail detail(Requisition r, User me) {
        Map<UUID, Project> projectMap = projects.byIds(r.getTenantId(), Set.of(r.getProjectId()));
        Set<UUID> ids = new HashSet<>();
        ids.add(r.getRequestedBy());
        r.getApprovals().stream().map(RequisitionApproval::getDecidedBy).filter(java.util.Objects::nonNull)
                .forEach(ids::add);
        Map<UUID, User> people = people(r.getTenantId(), ids.stream());

        boolean mine = r.getRequestedBy().equals(me.getId());
        Permissions can = new Permissions(
                mine && r.isEditable(),
                mine && r.isEditable() && !r.getLines().isEmpty(),
                r.canBeDecidedBy(me.getId(), me.getRoles()),
                mine && (r.isEditable() || r.getStatus() == RequisitionStatus.SUBMITTED));

        return new RequisitionDetail(r.getId(), r.getNumber(), r.getTitle(), r.getJustification(),
                r.getDepartment(), r.getNeededBy(), projectRef(projectMap.get(r.getProjectId()), r.getProjectId()),
                r.getStatus(), r.getCurrency(), r.getEstimatedTotalMinor(), r.getProposedMethodCode(),
                person(people, r.getRequestedBy()), r.getCurrentRound(), r.getCreatedAt(), r.getSubmittedAt(),
                r.getDecidedAt(), r.getUpdatedAt(), r.getVersion(),
                r.getLines().stream().map(l -> new LineResponse(l.getId(), l.getLineNo(), l.getDescription(),
                        l.getQuantity(), l.getUnit(), l.getUnitPriceMinor(), l.getTotalMinor())).toList(),
                r.getApprovals().stream().map(a -> new ApprovalResponse(a.getId(), a.getRound(), a.getStepOrder(),
                        a.getApproverRole(), a.getStatus(),
                        a.getDecidedBy() == null ? null : person(people, a.getDecidedBy()),
                        a.getDecidedAt(), a.getComment())).toList(),
                can, plan.lineFor(r.getTenantId(), r.getPlanLineId(), r.getId()), r.getUnplannedReason(),
                r.getPlanStatus());
    }

    private Map<UUID, User> people(UUID tenantId, Stream<UUID> ids) {
        return currentUser.byIds(tenantId, ids.collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private static PersonRef person(Map<UUID, User> people, UUID id) {
        User u = people.get(id);
        return new PersonRef(id, u == null ? "Unknown user" : u.getDisplayName());
    }

    private static ProjectRef projectRef(Project p, UUID id) {
        return p == null ? new ProjectRef(id, "?", "Unknown project") : new ProjectRef(p.getId(), p.getCode(), p.getName());
    }

    private static String link(Requisition r) {
        return "/requisitions/" + r.getId();
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
