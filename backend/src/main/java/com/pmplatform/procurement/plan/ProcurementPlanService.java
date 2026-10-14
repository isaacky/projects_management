package com.pmplatform.procurement.plan;

import com.pmplatform.platform.audit.AuditService;
import com.pmplatform.platform.json.JsonText;
import com.pmplatform.platform.notification.NotificationService;
import com.pmplatform.platform.notification.NotificationService.Message;
import com.pmplatform.platform.user.CurrentUserService;
import com.pmplatform.platform.user.Roles;
import com.pmplatform.platform.user.User;
import com.pmplatform.platform.web.BusinessRuleException;
import com.pmplatform.platform.web.ForbiddenActionException;
import com.pmplatform.platform.web.NotFoundException;
import com.pmplatform.portfolio.Project;
import com.pmplatform.portfolio.ProjectService;
import com.pmplatform.procurement.config.ProcurementMethod;
import com.pmplatform.procurement.config.ProcurementMethodRepository;
import com.pmplatform.procurement.config.ProcurementRules;
import com.pmplatform.procurement.plan.PlanActuals.LineActual;
import com.pmplatform.procurement.plan.PlanLine.LineInput;
import com.pmplatform.procurement.plan.api.PlanDtos.AvailableLine;
import com.pmplatform.procurement.plan.api.PlanDtos.Bucket;
import com.pmplatform.procurement.plan.api.PlanDtos.CreatePlanRequest;
import com.pmplatform.procurement.plan.api.PlanDtos.DepartmentRequest;
import com.pmplatform.procurement.plan.api.PlanDtos.DepartmentView;
import com.pmplatform.procurement.plan.api.PlanDtos.ImportResult;
import com.pmplatform.procurement.plan.api.PlanDtos.LineRequest;
import com.pmplatform.procurement.plan.api.PlanDtos.LineView;
import com.pmplatform.procurement.plan.api.PlanDtos.Permissions;
import com.pmplatform.procurement.plan.api.PlanDtos.PlanSummary;
import com.pmplatform.procurement.plan.api.PlanDtos.PlanView;
import com.pmplatform.procurement.plan.api.PlanDtos.ProjectRef;
import com.pmplatform.procurement.plan.api.PlanDtos.RevisionView;
import com.pmplatform.procurement.plan.api.PlanDtos.Summary;
import com.pmplatform.procurement.plan.api.PlanDtos.UnplannedView;
import com.pmplatform.procurement.tender.api.TenderDtos.Ref;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * The annual procurement plan: departments prepare their lines, procurement consolidates, the
 * accounting officer approves. Requisitions are then checked against the approved lines (see
 * {@link #checkRequisition}).
 */
@Service
public class ProcurementPlanService {

    static final ZoneId LOCAL_ZONE = ZoneId.of("Africa/Nairobi");
    static final long MAX_IMPORT_BYTES = 2L * 1024 * 1024;
    /** Requisitions drawing on no line must say why in at least this many characters. */
    public static final int MIN_UNPLANNED_REASON = 20;

    private final ProcurementPlanRepository plans;
    private final PlanDepartmentRepository departments;
    private final PlanLineRepository lines;
    private final PlanRevisionRepository revisions;
    private final PlanActuals actuals;
    private final ProcurementMethodRepository methods;
    private final ProjectService projects;
    private final CurrentUserService currentUser;
    private final NotificationService notifications;
    private final AuditService audit;
    private final Clock clock;

    public ProcurementPlanService(ProcurementPlanRepository plans, PlanDepartmentRepository departments,
            PlanLineRepository lines, PlanRevisionRepository revisions, PlanActuals actuals,
            ProcurementMethodRepository methods, ProjectService projects, CurrentUserService currentUser,
            NotificationService notifications, AuditService audit, Clock clock) {
        this.plans = plans;
        this.departments = departments;
        this.lines = lines;
        this.revisions = revisions;
        this.actuals = actuals;
        this.methods = methods;
        this.projects = projects;
        this.currentUser = currentUser;
        this.notifications = notifications;
        this.audit = audit;
        this.clock = clock;
    }

    // ================================================================== queries

    @Transactional(readOnly = true)
    public List<PlanSummary> list(UUID tenantId) {
        List<ProcurementPlan> all = plans.findByTenantIdOrderByFyStartDesc(tenantId);
        Map<UUID, List<PlanLine>> byPlan = lines.findByTenantIdAndPlanIdIn(tenantId,
                all.stream().map(ProcurementPlan::getId).toList()).stream()
                .collect(Collectors.groupingBy(PlanLine::getPlanId));
        LocalDate today = today();
        return all.stream().map(p -> {
            List<PlanLine> active = byPlan.getOrDefault(p.getId(), List.of()).stream()
                    .filter(l -> !l.isCancelled()).toList();
            return new PlanSummary(p.getId(), p.getFinancialYear(), p.getFyStart(), p.getFyEnd(), p.getStatus(),
                    p.getRevision(), active.stream().mapToLong(PlanLine::getEstimateMinor).sum(), active.size(),
                    departments.findByTenantIdAndPlanIdOrderByNameAsc(tenantId, p.getId()).size(), p.covers(today));
        }).toList();
    }

    @Transactional(readOnly = true)
    public PlanView get(UUID tenantId, UUID planId) {
        return view(load(tenantId, planId), currentUser.require());
    }

    /** Approved lines of the plan(s) covering today, for the requisition form. */
    @Transactional(readOnly = true)
    public List<AvailableLine> availableLines(UUID tenantId, UUID projectId) {
        LocalDate today = today();
        List<ProcurementPlan> covering = plans.findByTenantIdOrderByFyStartDesc(tenantId).stream()
                .filter(p -> p.everApproved() && p.covers(today)).toList();
        if (covering.isEmpty()) {
            return List.of();
        }
        Map<UUID, ProcurementPlan> planMap = covering.stream()
                .collect(Collectors.toMap(ProcurementPlan::getId, Function.identity()));
        Map<UUID, LineActual> act = actuals.forPlans(tenantId, List.copyOf(planMap.keySet()));
        Map<UUID, String> deptNames = new HashMap<>();
        for (ProcurementPlan p : covering) {
            departments.findByTenantIdAndPlanIdOrderByNameAsc(tenantId, p.getId())
                    .forEach(d -> deptNames.put(d.getId(), d.getName()));
        }
        return lines.findByTenantIdAndPlanIdIn(tenantId, planMap.keySet()).stream()
                .filter(PlanLine::isUsable)
                .sorted(Comparator.comparing((PlanLine l) -> projectId == null || !projectId.equals(l.getProjectId()))
                        .thenComparing(PlanLine::getLineNo))
                .map(l -> available(l, planMap.get(l.getPlanId()), deptNames.get(l.getDepartmentId()),
                        act.getOrDefault(l.getId(), LineActual.NONE).committedMinor()))
                .toList();
    }

    /** The plan line a requisition names, for its detail page (null when there is none). */
    @Transactional(readOnly = true)
    public AvailableLine lineFor(UUID tenantId, UUID lineId, UUID requisitionId) {
        if (lineId == null) {
            return null;
        }
        PlanLine l = lines.findByTenantIdAndId(tenantId, lineId).orElse(null);
        if (l == null) {
            return null;
        }
        ProcurementPlan p = load(tenantId, l.getPlanId());
        String dept = departments.findById(l.getDepartmentId()).map(PlanDepartment::getName).orElse("?");
        return available(l, p, dept, actuals.committed(l.getId(), requisitionId));
    }

    // ================================================================== requisitions

    /**
     * A requisition may name a plan line only if the line is approved and not dropped. Returns the
     * line's department name, which the requisition takes when it has none.
     */
    @Transactional(readOnly = true)
    public String checkLink(UUID tenantId, UUID lineId, String currency) {
        PlanLine l = lines.findByTenantIdAndId(tenantId, lineId)
                .orElseThrow(() -> new BusinessRuleException("That plan line does not exist"));
        if (!l.isUsable()) {
            throw new BusinessRuleException(l.isCancelled() ? "That plan line was dropped from the plan"
                    : "That plan line has not been approved yet");
        }
        requireCurrency(load(tenantId, l.getPlanId()), currency);
        return departments.findById(l.getDepartmentId()).map(PlanDepartment::getName).orElse(null);
    }

    /**
     * Decides how a requisition being submitted relates to the plan. A named line must belong to
     * the approved plan for the current financial year and have enough balance left; otherwise,
     * when an approved plan covers today, the requisition is unplanned and needs a reason.
     */
    @Transactional
    public PlanCoverage checkRequisition(UUID tenantId, UUID requisitionId, UUID lineId, String unplannedReason,
            long amountMinor, String currency) {
        LocalDate today = today();
        if (lineId != null) {
            PlanLine l = lines.lock(tenantId, lineId)
                    .orElseThrow(() -> new BusinessRuleException("That plan line does not exist"));
            ProcurementPlan p = load(tenantId, l.getPlanId());
            String ref = reference(p, l);
            if (!l.isUsable()) {
                throw new BusinessRuleException(ref + (l.isCancelled() ? " was dropped from the plan"
                        : " has not been approved yet"));
            }
            requireCurrency(p, currency);
            if (!p.covers(today)) {
                throw new BusinessRuleException(ref + " is in the " + p.getFinancialYear()
                        + " plan; choose a line from the current year's plan");
            }
            long balance = PlanRules.balance(l.getApprovedEstimateMinor(), actuals.committed(l.getId(), requisitionId));
            if (amountMinor > balance) {
                throw new BusinessRuleException("This requisition (" + money(amountMinor) + ") is more than what is "
                        + "left on " + ref + " (" + money(balance) + "). Revise the plan, or raise it as unplanned "
                        + "with a reason.");
            }
            return PlanCoverage.PLANNED;
        }
        boolean planned = plans.findByTenantIdOrderByFyStartDesc(tenantId).stream()
                .anyMatch(p -> p.everApproved() && p.covers(today));
        if (!planned) {
            return PlanCoverage.NO_PLAN;
        }
        if (unplannedReason == null || unplannedReason.trim().length() < MIN_UNPLANNED_REASON) {
            throw new BusinessRuleException("Choose the procurement plan line this requisition draws on, or explain "
                    + "why it is not in the plan (at least " + MIN_UNPLANNED_REASON + " characters). Unplanned "
                    + "requisitions also go to the accounting officer.");
        }
        return PlanCoverage.UNPLANNED;
    }

    private static void requireCurrency(ProcurementPlan p, String currency) {
        if (!p.getCurrency().equals(currency)) {
            throw new BusinessRuleException("The procurement plan is in " + p.getCurrency() + "; a requisition in "
                    + currency + " cannot draw on its lines");
        }
    }

    // ================================================================== plan

    @Transactional
    public PlanView create(UUID tenantId, CreatePlanRequest req) {
        User me = requireProcurement("prepare procurement plans");
        LocalDate end = PlanRules.fyEnd(req.fyStart());
        plans.findByTenantIdOrderByFyStartDesc(tenantId).stream()
                .filter(x -> !x.getFyStart().isAfter(end) && !x.getFyEnd().isBefore(req.fyStart()))
                .findFirst().ifPresent(x -> {
                    throw new BusinessRuleException("The " + x.getFinancialYear() + " plan already covers part of "
                            + "that year (" + x.getFyStart() + " to " + x.getFyEnd() + ")");
                });
        Instant now = now();
        ProcurementPlan p = plans.save(new ProcurementPlan(tenantId, req.fyStart(), me.getId(), now));
        int copied = 0;
        if (Boolean.TRUE.equals(req.copyDepartments())) {
            Optional<ProcurementPlan> previous = plans.findByTenantIdOrderByFyStartDesc(tenantId).stream()
                    .filter(x -> x.getFyStart().isBefore(req.fyStart())).findFirst();
            if (previous.isPresent()) {
                List<PlanDepartment> old = departments.findByTenantIdAndPlanIdOrderByNameAsc(tenantId,
                        previous.get().getId());
                Set<UUID> active = activeUsers(tenantId, old.stream().map(PlanDepartment::getHeadId).toList());
                for (PlanDepartment d : old) {
                    if (active.contains(d.getHeadId())) {
                        PlanDepartment n = new PlanDepartment(tenantId, p.getId(), now);
                        n.define(d.getName(), d.getHeadId(), null, now);
                        departments.save(n);
                        copied++;
                    }
                }
            }
        }
        audit.record(tenantId, "plan.created", "ProcurementPlan", p.getId(),
                JsonText.object("financialYear", p.getFinancialYear(), "fyStart", p.getFyStart(),
                        "departmentsCopied", copied));
        if (copied > 0) {
            departments.flush();
            departments.findByTenantIdAndPlanIdOrderByNameAsc(tenantId, p.getId())
                    .forEach(d -> notifyHead(tenantId, p, d, me));
        }
        return view(p, me);
    }

    @Transactional
    public PlanView updateSettings(UUID tenantId, UUID planId, BigDecimal agpoTargetPct) {
        User me = requireProcurement("change plan settings");
        ProcurementPlan p = load(tenantId, planId);
        BigDecimal before = p.getAgpoTargetPct();
        p.setAgpoTarget(agpoTargetPct, now());
        audit.record(tenantId, "plan.settings", "ProcurementPlan", planId,
                JsonText.object("agpoTargetPct", agpoTargetPct, "previous", before));
        return view(p, me);
    }

    @Transactional
    public PlanView submit(UUID tenantId, UUID planId) {
        User me = currentUser.require();
        Roles.requireAny(me, Set.of(Roles.HEAD_OF_PROCUREMENT), "submit the procurement plan");
        ProcurementPlan p = lock(tenantId, planId);
        p.requireDraft("submit it");
        List<PlanDepartment> depts = departments.findByTenantIdAndPlanIdOrderByNameAsc(tenantId, planId);
        if (depts.isEmpty()) {
            throw new BusinessRuleException("Add the departments before submitting the plan");
        }
        List<String> pending = depts.stream().filter(PlanDepartment::isDraft).map(PlanDepartment::getName).toList();
        if (!pending.isEmpty()) {
            throw new BusinessRuleException("Still to submit their lines: " + String.join(", ", pending));
        }
        List<PlanLine> all = lines.findByTenantIdAndPlanIdOrderByLineNoAsc(tenantId, planId);
        List<PlanLine> active = all.stream().filter(l -> !l.isCancelled()).toList();
        if (active.isEmpty()) {
            throw new BusinessRuleException("The plan has no lines");
        }
        Map<UUID, Long> totals = totalsByDepartment(active);
        List<String> over = depts.stream().filter(d -> d.overCeiling(totals.getOrDefault(d.getId(), 0L)))
                .map(PlanDepartment::getName).toList();
        if (!over.isEmpty()) {
            throw new BusinessRuleException("Over their budget ceiling: " + String.join(", ", over));
        }
        boolean someoneElse = currentUser.withRole(tenantId, Roles.ACCOUNTING_OFFICER).stream()
                .anyMatch(u -> !u.getId().equals(me.getId()) && u.isActive());
        if (!someoneElse) {
            throw new BusinessRuleException("Nobody else holds the ACCOUNTING_OFFICER role to approve the plan");
        }
        p.submit(me.getId(), now());
        long total = active.stream().mapToLong(PlanLine::getEstimateMinor).sum();
        audit.record(tenantId, "plan.submitted", "ProcurementPlan", planId,
                JsonText.object("financialYear", p.getFinancialYear(), "revision", p.getRevision() + 1,
                        "lines", active.size(), "totalMinor", total));
        notifications.notifyRole(tenantId, Roles.ACCOUNTING_OFFICER, new Message("plan.submitted",
                "Procurement plan " + p.getFinancialYear() + " for approval",
                cap(active.size() + " lines, " + money(total) + (p.everApproved() ? ". Revision: "
                        + p.getRevisionReason() : "."), 1000), link(p)));
        return view(p, me);
    }

    @Transactional
    public PlanView approve(UUID tenantId, UUID planId, String note) {
        User me = currentUser.require();
        Roles.requireAny(me, Set.of(Roles.ACCOUNTING_OFFICER), "approve the procurement plan");
        ProcurementPlan p = lock(tenantId, planId);
        if (me.getId().equals(p.getSubmittedBy())) {
            throw new ForbiddenActionException("You submitted this plan; another accounting officer approves it");
        }
        String reason = p.getRevisionReason();
        List<PlanLine> active = lines.findByTenantIdAndPlanIdOrderByLineNoAsc(tenantId, planId).stream()
                .filter(l -> !l.isCancelled()).toList();
        for (PlanLine l : active) {
            if (l.change() != null && l.getApprovedEstimateMinor() != null) {
                lines.lock(tenantId, l.getId());
                long committed = actuals.committed(l.getId(), new UUID(0, 0));
                if (l.getEstimateMinor() < committed) {
                    throw new BusinessRuleException(reference(p, l) + ": requisitions now draw " + money(committed)
                            + ", more than its revised estimate; return the plan so it can be corrected");
                }
            }
        }
        Instant now = now();
        int revision = p.approve(now);
        active.forEach(l -> l.approved(revision, now));
        long total = active.stream().mapToLong(PlanLine::getEstimateMinor).sum();
        revisions.save(new PlanRevision(tenantId, planId, revision, me.getId(), total, active.size(), reason,
                blank(note), now));
        audit.record(tenantId, revision == 1 ? "plan.approved" : "plan.revision_approved", "ProcurementPlan", planId,
                JsonText.object("financialYear", p.getFinancialYear(), "revision", revision, "totalMinor", total,
                        "lines", active.size(), "note", blank(note)));
        Set<UUID> tell = new HashSet<>();
        if (p.getSubmittedBy() != null) {
            tell.add(p.getSubmittedBy());
        }
        departments.findByTenantIdAndPlanIdOrderByNameAsc(tenantId, planId).forEach(d -> tell.add(d.getHeadId()));
        tell.remove(me.getId());
        notifications.notifyUsers(tenantId, tell, new Message("plan.approved",
                "Procurement plan " + p.getFinancialYear() + " approved" + (revision > 1 ? " (revision " + revision
                        + ")" : ""),
                cap("Requisitions can now draw on its lines." + (blank(note) == null ? "" : " " + note.trim()), 1000),
                link(p)));
        return view(p, me);
    }

    @Transactional
    public PlanView returnPlan(UUID tenantId, UUID planId, String note) {
        User me = currentUser.require();
        Roles.requireAny(me, Set.of(Roles.ACCOUNTING_OFFICER), "return the procurement plan");
        ProcurementPlan p = lock(tenantId, planId);
        if (me.getId().equals(p.getSubmittedBy())) {
            throw new ForbiddenActionException("You submitted this plan; another accounting officer decides on it");
        }
        p.returnForChanges(note, now());
        audit.record(tenantId, "plan.returned", "ProcurementPlan", planId,
                JsonText.object("financialYear", p.getFinancialYear(), "note", note));
        if (p.getSubmittedBy() != null) {
            notifications.notifyUsers(tenantId, List.of(p.getSubmittedBy()), new Message("plan.returned",
                    "Procurement plan " + p.getFinancialYear() + " returned", cap(note.trim(), 1000), link(p)));
        }
        return view(p, me);
    }

    @Transactional
    public PlanView revise(UUID tenantId, UUID planId, String reason) {
        User me = currentUser.require();
        Roles.requireAny(me, Set.of(Roles.HEAD_OF_PROCUREMENT), "revise the procurement plan");
        ProcurementPlan p = lock(tenantId, planId);
        if (reason != null && reason.trim().length() > 2000) {
            throw new BusinessRuleException("The reason is at most 2,000 characters");
        }
        p.reopen(reason, now());
        audit.record(tenantId, "plan.reopened", "ProcurementPlan", planId,
                JsonText.object("financialYear", p.getFinancialYear(), "reason", reason));
        notifications.notifyRole(tenantId, Roles.ACCOUNTING_OFFICER, new Message("plan.reopened",
                "Procurement plan " + p.getFinancialYear() + " reopened for revision", cap(reason.trim(), 1000),
                link(p)));
        return view(p, me);
    }

    // ================================================================== departments

    @Transactional
    public PlanView addDepartment(UUID tenantId, UUID planId, DepartmentRequest req) {
        User me = requireProcurement("add departments to the plan");
        ProcurementPlan p = lock(tenantId, planId);
        p.requireDraft("add departments");
        checkHead(tenantId, req.headId());
        checkUniqueName(tenantId, planId, null, req.name());
        Instant now = now();
        PlanDepartment d = new PlanDepartment(tenantId, planId, now);
        d.define(req.name(), req.headId(), req.ceilingMinor(), now);
        departments.save(d);
        audit.record(tenantId, "plan.department_added", "ProcurementPlan", planId,
                JsonText.object("department", d.getName(), "head", d.getHeadId(), "ceilingMinor", d.getCeilingMinor()));
        notifyHead(tenantId, p, d, me);
        return view(p, me);
    }

    @Transactional
    public PlanView editDepartment(UUID tenantId, UUID planId, UUID deptId, DepartmentRequest req) {
        User me = requireProcurement("change plan departments");
        ProcurementPlan p = lock(tenantId, planId);
        p.requireDraft("change departments");
        PlanDepartment d = department(tenantId, planId, deptId);
        checkHead(tenantId, req.headId());
        checkUniqueName(tenantId, planId, deptId, req.name());
        UUID oldHead = d.getHeadId();
        d.define(req.name(), req.headId(), req.ceilingMinor(), now());
        audit.record(tenantId, "plan.department_changed", "ProcurementPlan", planId,
                JsonText.object("department", d.getName(), "head", d.getHeadId(), "ceilingMinor", d.getCeilingMinor()));
        if (!oldHead.equals(d.getHeadId())) {
            notifyHead(tenantId, p, d, me);
        }
        return view(p, me);
    }

    @Transactional
    public PlanView deleteDepartment(UUID tenantId, UUID planId, UUID deptId) {
        User me = requireProcurement("remove plan departments");
        ProcurementPlan p = lock(tenantId, planId);
        p.requireDraft("remove departments");
        PlanDepartment d = department(tenantId, planId, deptId);
        if (lines.countByDepartmentId(deptId) > 0) {
            throw new BusinessRuleException(d.getName() + " has lines; move or delete them first");
        }
        departments.delete(d);
        audit.record(tenantId, "plan.department_removed", "ProcurementPlan", planId,
                JsonText.object("department", d.getName()));
        return view(p, me);
    }

    @Transactional
    public PlanView submitDepartment(UUID tenantId, UUID planId, UUID deptId) {
        User me = currentUser.require();
        ProcurementPlan p = lock(tenantId, planId);
        p.requireDraft("submit department lines");
        PlanDepartment d = department(tenantId, planId, deptId);
        if (!me.getId().equals(d.getHeadId()) && !isProcurement(me)) {
            throw new ForbiddenActionException("The head of " + d.getName() + " or procurement submits its lines");
        }
        List<PlanLine> active = lines.findByTenantIdAndPlanIdOrderByLineNoAsc(tenantId, planId).stream()
                .filter(l -> l.getDepartmentId().equals(deptId) && !l.isCancelled()).toList();
        long total = active.stream().mapToLong(PlanLine::getEstimateMinor).sum();
        d.submit(me.getId(), total, now());
        audit.record(tenantId, "plan.department_submitted", "ProcurementPlan", planId,
                JsonText.object("department", d.getName(), "lines", active.size(), "totalMinor", total));
        notifications.notifyRole(tenantId, Roles.HEAD_OF_PROCUREMENT, new Message("plan.department_submitted",
                cap(d.getName() + " submitted its " + p.getFinancialYear() + " plan", 200),
                active.size() + " lines, " + money(total), link(p)));
        return view(p, me);
    }

    @Transactional
    public PlanView returnDepartment(UUID tenantId, UUID planId, UUID deptId, String note) {
        User me = requireProcurement("return department plans");
        ProcurementPlan p = lock(tenantId, planId);
        p.requireDraft("return department lines");
        PlanDepartment d = department(tenantId, planId, deptId);
        d.returnToHead(note, now());
        audit.record(tenantId, "plan.department_returned", "ProcurementPlan", planId,
                JsonText.object("department", d.getName(), "note", note));
        notifications.notifyUsers(tenantId, List.of(d.getHeadId()), new Message("plan.department_returned",
                cap(d.getName() + "'s " + p.getFinancialYear() + " plan returned", 200), cap(note.trim(), 1000),
                link(p)));
        return view(p, me);
    }

    // ================================================================== lines

    @Transactional
    public PlanView addLine(UUID tenantId, UUID planId, LineRequest req) {
        User me = currentUser.require();
        ProcurementPlan p = lock(tenantId, planId);
        PlanDepartment d = department(tenantId, planId, req.departmentId());
        requireLineEditor(p, d, me);
        LineInput in = input(tenantId, req, methodList(tenantId));
        Instant now = now();
        PlanLine l = new PlanLine(tenantId, planId, d.getId(), p.takeLineNo(), me.getId(), now);
        l.define(in, now);
        lines.save(l);
        audit.record(tenantId, "plan.line_added", "ProcurementPlan", planId, lineAudit(p, l, d));
        return view(p, me);
    }

    @Transactional
    public PlanView editLine(UUID tenantId, UUID planId, UUID lineId, LineRequest req) {
        User me = currentUser.require();
        ProcurementPlan p = lock(tenantId, planId);
        PlanLine l = line(tenantId, planId, lineId);
        PlanDepartment from = department(tenantId, planId, l.getDepartmentId());
        requireLineEditor(p, from, me);
        PlanDepartment to = from;
        if (!req.departmentId().equals(l.getDepartmentId())) {
            to = department(tenantId, planId, req.departmentId());
            requireLineEditor(p, to, me);
        }
        LineInput in = input(tenantId, req, methodList(tenantId));
        lines.lock(tenantId, lineId); // wait for any requisition being submitted against this line
        long committed = actuals.committed(l.getId(), new UUID(0, 0));
        if (in.estimateMinor() < committed) {
            throw new BusinessRuleException("Requisitions already draw " + money(committed) + " on this line; the "
                    + "estimate cannot be lower");
        }
        Instant now = now();
        l.define(in, now);
        if (to != from) {
            l.moveTo(to.getId(), now);
        }
        audit.record(tenantId, "plan.line_changed", "ProcurementPlan", planId, lineAudit(p, l, to));
        return view(p, me);
    }

    @Transactional
    public PlanView deleteLine(UUID tenantId, UUID planId, UUID lineId) {
        User me = currentUser.require();
        ProcurementPlan p = lock(tenantId, planId);
        PlanLine l = line(tenantId, planId, lineId);
        PlanDepartment d = department(tenantId, planId, l.getDepartmentId());
        requireLineEditor(p, d, me);
        if (l.getApprovedEstimateMinor() != null || actuals.anyRequisition(l.getId())) {
            throw new BusinessRuleException("Line " + l.getLineNo() + " was approved or has requisitions; drop it "
                    + "with a reason instead");
        }
        lines.delete(l);
        audit.record(tenantId, "plan.line_deleted", "ProcurementPlan", planId, lineAudit(p, l, d));
        return view(p, me);
    }

    @Transactional
    public PlanView cancelLine(UUID tenantId, UUID planId, UUID lineId, String note) {
        User me = currentUser.require();
        ProcurementPlan p = lock(tenantId, planId);
        PlanLine l = line(tenantId, planId, lineId);
        PlanDepartment d = department(tenantId, planId, l.getDepartmentId());
        requireLineEditor(p, d, me);
        lines.lock(tenantId, lineId);
        long committed = actuals.committed(l.getId(), new UUID(0, 0));
        if (committed > 0) {
            throw new BusinessRuleException("Requisitions already draw on line " + l.getLineNo()
                    + "; it cannot be dropped");
        }
        l.cancel(note, now());
        audit.record(tenantId, "plan.line_dropped", "ProcurementPlan", planId,
                JsonText.object("line", reference(p, l), "description", l.getDescription(), "note", note));
        return view(p, me);
    }

    /** Adds the lines in a CSV file to a department. All or nothing: any row error rejects the file. */
    @Transactional
    public ImportResult importLines(UUID tenantId, UUID planId, UUID deptId, MultipartFile file) {
        User me = currentUser.require();
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("Choose the CSV file");
        }
        if (file.getSize() > MAX_IMPORT_BYTES) {
            throw new BusinessRuleException("The file is larger than 2 MB");
        }
        ProcurementPlan p = lock(tenantId, planId);
        PlanDepartment d = department(tenantId, planId, deptId);
        requireLineEditor(p, d, me);
        String text;
        try {
            text = new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        PlanCsv.Result parsed = PlanCsv.parse(text);
        if (!parsed.fileErrors().isEmpty()) {
            return new ImportResult(0, parsed.fileErrors(), null);
        }
        if (parsed.rows().isEmpty()) {
            return new ImportResult(0, List.of("The file has no lines under the header"), null);
        }
        List<ProcurementMethod> methodList = methodList(tenantId);
        Map<String, UUID> projectCodes = new HashMap<>();
        projects.list(tenantId, null).forEach(pr -> projectCodes.put(pr.getCode().toUpperCase(Locale.ROOT), pr.getId()));
        List<String> errors = new ArrayList<>();
        List<LineInput> inputs = new ArrayList<>();
        for (PlanCsv.Row row : parsed.rows()) {
            List<String> rowErrors = new ArrayList<>(row.errors());
            PlanCsv.Draft dr = row.draft();
            ProcurementMethod m = dr.method() == null ? null : resolveMethod(methodList, dr.method());
            if (dr.method() != null && m == null) {
                rowErrors.add("method \"" + dr.method() + "\" is not one of the methods in Procurement settings");
            }
            UUID projectId = null;
            if (dr.projectCode() != null) {
                projectId = projectCodes.get(dr.projectCode().toUpperCase(Locale.ROOT));
                if (projectId == null) {
                    rowErrors.add("no project with code " + dr.projectCode());
                }
            }
            if (rowErrors.isEmpty()) {
                LineInput in = new LineInput(dr.description(), dr.category(), dr.quantity(), dr.unit(),
                        dr.estimateMinor(), m.getCode(), dr.quarter(), dr.sourceOfFunds(), dr.budgetCode(),
                        dr.reserved(), projectId);
                try {
                    checkMethodFits(methodList, in.methodCode(), in.estimateMinor());
                    new PlanLine(tenantId, planId, deptId, 1, me.getId(), now()).define(in, now()); // field checks
                    inputs.add(in);
                } catch (BusinessRuleException e) {
                    rowErrors.add(e.getMessage());
                }
            }
            if (!rowErrors.isEmpty() && errors.size() < 50) {
                errors.add("Row " + row.line() + ": " + String.join("; ", rowErrors));
            }
        }
        if (!errors.isEmpty()) {
            return new ImportResult(0, errors, null);
        }
        Instant now = now();
        long total = 0;
        for (LineInput in : inputs) {
            PlanLine l = new PlanLine(tenantId, planId, deptId, p.takeLineNo(), me.getId(), now);
            l.define(in, now);
            lines.save(l);
            total += in.estimateMinor();
        }
        audit.record(tenantId, "plan.lines_imported", "ProcurementPlan", planId,
                JsonText.object("department", d.getName(), "lines", inputs.size(), "totalMinor", total,
                        "file", file.getOriginalFilename()));
        lines.flush();
        return new ImportResult(inputs.size(), List.of(), view(p, me));
    }

    // ================================================================== view

    private PlanView view(ProcurementPlan p, User me) {
        UUID tenantId = p.getTenantId();
        LocalDate today = today();
        List<PlanDepartment> depts = departments.findByTenantIdAndPlanIdOrderByNameAsc(tenantId, p.getId());
        Map<UUID, PlanDepartment> deptMap = depts.stream()
                .collect(Collectors.toMap(PlanDepartment::getId, Function.identity()));
        List<PlanLine> all = lines.findByTenantIdAndPlanIdOrderByLineNoAsc(tenantId, p.getId());
        List<PlanLine> active = all.stream().filter(l -> !l.isCancelled()).toList();
        Map<UUID, LineActual> act = actuals.forPlans(tenantId, List.of(p.getId()));
        List<PlanRevision> revs = revisions.findByTenantIdAndPlanIdOrderByRevisionDesc(tenantId, p.getId());
        Map<UUID, Project> projectMap = projects.byIds(tenantId, all.stream().map(PlanLine::getProjectId)
                .filter(Objects::nonNull).collect(Collectors.toSet()));

        Set<UUID> userIds = new HashSet<>();
        depts.forEach(d -> {
            userIds.add(d.getHeadId());
            userIds.add(d.getSubmittedBy());
        });
        revs.forEach(r -> userIds.add(r.getApprovedBy()));
        userIds.add(p.getSubmittedBy());
        userIds.add(p.getCreatedBy());
        Map<UUID, User> people = people(tenantId, userIds);

        boolean procurement = isProcurement(me);
        boolean hop = me.hasRole(Roles.HEAD_OF_PROCUREMENT);
        boolean ao = me.hasRole(Roles.ACCOUNTING_OFFICER);
        Map<UUID, Long> totals = totalsByDepartment(active);
        Map<UUID, Long> counts = active.stream()
                .collect(Collectors.groupingBy(PlanLine::getDepartmentId, Collectors.counting()));
        Map<UUID, Boolean> canEditLines = new HashMap<>();

        List<DepartmentView> deptViews = new ArrayList<>();
        for (PlanDepartment d : depts) {
            long total = totals.getOrDefault(d.getId(), 0L);
            boolean edit = canEditLines(p, d, me);
            canEditLines.put(d.getId(), edit);
            boolean head = me.getId().equals(d.getHeadId());
            int lineCount = counts.getOrDefault(d.getId(), 0L).intValue();
            boolean anyLines = all.stream().anyMatch(l -> l.getDepartmentId().equals(d.getId()));
            deptViews.add(new DepartmentView(d.getId(), d.getName(), ref(people, d.getHeadId()), d.getCeilingMinor(),
                    d.getStatus(), d.getReturnNote(), ref(people, d.getSubmittedBy()), d.getSubmittedAt(), total,
                    lineCount, d.overCeiling(total), edit, p.isDraft() && d.isDraft() && (head || procurement),
                    p.isDraft() && !d.isDraft() && procurement, p.isDraft() && procurement,
                    p.isDraft() && procurement && !anyLines, d.getVersion()));
        }

        List<LineView> lineViews = new ArrayList<>();
        long committedTotal = 0;
        long awardedTotal = 0;
        int behindCount = 0;
        for (PlanLine l : all) {
            LineActual a = act.getOrDefault(l.getId(), LineActual.NONE);
            LineStage stage = PlanRules.stage(a.committedMinor(), a.activeTenders(), a.awardedMinor());
            boolean behind = !l.isCancelled()
                    && PlanRules.behind(l.isUsable(), stage, p.getFyStart(), l.getQuarter(), today);
            if (!l.isCancelled()) {
                committedTotal += a.committedMinor();
                awardedTotal += a.awardedMinor();
                behindCount += behind ? 1 : 0;
            }
            boolean edit = canEditLines.getOrDefault(l.getDepartmentId(), false) && !l.isCancelled();
            Project pr = l.getProjectId() == null ? null : projectMap.get(l.getProjectId());
            PlanDepartment d = deptMap.get(l.getDepartmentId());
            lineViews.add(new LineView(l.getId(), l.getLineNo(), reference(p, l), l.getDepartmentId(),
                    d == null ? "?" : d.getName(), l.getDescription(), l.getCategory(), l.getQuantity(), l.getUnit(),
                    l.getEstimateMinor(), l.getMethodCode(), l.getQuarter(), l.getSourceOfFunds(), l.getBudgetCode(),
                    l.getReservedGroup(), pr == null ? null : new ProjectRef(pr.getId(), pr.getCode(), pr.getName()),
                    l.getApprovedEstimateMinor(), l.change(), l.isCancelled(), l.getCancelNote(), a.committedMinor(),
                    l.getApprovedEstimateMinor() == null ? 0
                            : PlanRules.balance(l.getApprovedEstimateMinor(), a.committedMinor()),
                    a.requisitions(), a.awardedMinor(), stage, behind, edit,
                    edit && l.getApprovedEstimateMinor() == null && a.requisitions() == 0,
                    edit && l.getApprovedEstimateMinor() != null && a.committedMinor() == 0, l.getVersion()));
        }

        long total = active.stream().mapToLong(PlanLine::getEstimateMinor).sum();
        long approvedTotal = active.stream().map(PlanLine::getApprovedEstimateMinor).filter(Objects::nonNull)
                .mapToLong(Long::longValue).sum();
        long agpo = active.stream().filter(l -> l.getReservedGroup() != null).mapToLong(PlanLine::getEstimateMinor)
                .sum();
        List<PlanActuals.Unplanned> unplanned = actuals.unplanned(tenantId, p.getFyStart(), p.getFyEnd());
        Summary summary = new Summary(total, approvedTotal, active.size(), committedTotal, awardedTotal, agpo,
                PlanRules.pct(agpo, total), p.getAgpoTargetPct(), behindCount, unplanned.size(),
                unplanned.stream().mapToLong(PlanActuals.Unplanned::amountMinor).sum(),
                buckets(active, act, l -> "Q" + l.getQuarter(), List.of("Q1", "Q2", "Q3", "Q4")),
                buckets(active, act, l -> l.getCategory().name(), null),
                buckets(active, act, PlanLine::getMethodCode, null));

        List<String> warnings = new ArrayList<>();
        if (total > 0 && PlanRules.pct(agpo, total).compareTo(p.getAgpoTargetPct()) < 0) {
            warnings.add("Lines reserved for AGPO groups come to " + PlanRules.pct(agpo, total) + "% of the plan, below "
                    + "the " + p.getAgpoTargetPct().stripTrailingZeros().toPlainString() + "% target");
        }
        deptViews.stream().filter(DepartmentView::overCeiling)
                .forEach(d -> warnings.add(d.name() + " is over its budget ceiling"));
        if (p.isDraft() && p.everApproved()) {
            long changed = active.stream().filter(l -> l.change() != null).count();
            warnings.add("Being revised: " + changed + " new or changed line" + (changed == 1 ? "" : "s")
                    + ". Requisitions use the approved amounts until the revision is approved; a dropped line "
                    + "stops taking requisitions at once.");
        }

        Permissions can = new Permissions(procurement, hop && p.isDraft(),
                ao && p.getStatus() == PlanStatus.SUBMITTED && !me.getId().equals(p.getSubmittedBy()),
                hop && p.getStatus() == PlanStatus.APPROVED, procurement && p.isDraft());
        return new PlanView(p.getId(), p.getFinancialYear(), p.getFyStart(), p.getFyEnd(), p.getCurrency(),
                p.getStatus(), p.getRevision(), p.getAgpoTargetPct(), p.getRevisionReason(), p.getReturnNote(),
                ref(people, p.getSubmittedBy()), p.getSubmittedAt(), ref(people, p.getCreatedBy()), p.getCreatedAt(),
                p.getVersion(), PlanRules.quarterOf(p.getFyStart(), today), deptViews, lineViews, summary,
                revs.stream().map(r -> new RevisionView(r.getRevision(), ref(people, r.getApprovedBy()),
                        r.getApprovedAt(), r.getTotalMinor(), r.getLineCount(), r.getReason(), r.getNote())).toList(),
                unplanned.stream().map(u -> new UnplannedView(u.requisitionId(), u.number(), u.title(),
                        u.amountMinor(), u.reason(), u.status(), u.submittedAt())).toList(),
                warnings, can);
    }

    private static List<Bucket> buckets(List<PlanLine> active, Map<UUID, LineActual> act,
            Function<PlanLine, String> key, List<String> fixedOrder) {
        Map<String, long[]> m = new LinkedHashMap<>();
        if (fixedOrder != null) {
            fixedOrder.forEach(k -> m.put(k, new long[3]));
        }
        for (PlanLine l : active) {
            long[] v = m.computeIfAbsent(key.apply(l), k -> new long[3]);
            v[0] += l.getEstimateMinor();
            v[1] += act.getOrDefault(l.getId(), LineActual.NONE).committedMinor();
            v[2]++;
        }
        List<Bucket> out = new ArrayList<>();
        m.forEach((k, v) -> out.add(new Bucket(k, v[0], v[1], (int) v[2])));
        if (fixedOrder == null) {
            out.sort(Comparator.comparingLong(Bucket::plannedMinor).reversed());
        }
        return out;
    }

    // ================================================================== helpers

    private LineInput input(UUID tenantId, LineRequest req, List<ProcurementMethod> methodList) {
        ProcurementMethod m = methodList.stream().filter(x -> x.getCode().equals(req.methodCode())).findFirst()
                .orElseThrow(() -> new BusinessRuleException("Unknown procurement method " + req.methodCode()));
        checkMethodFits(methodList, m.getCode(), req.estimateMinor());
        if (req.projectId() != null && projects.byIds(tenantId, Set.of(req.projectId())).isEmpty()) {
            throw new BusinessRuleException("That project does not exist");
        }
        return new LineInput(req.description(), req.category(), req.quantity(), req.unit(), req.estimateMinor(),
                m.getCode(), req.quarter(), req.sourceOfFunds(), req.budgetCode(), req.reservedGroup(),
                req.projectId());
    }

    /** The method must be enabled and its value range must cover the estimate (no splitting to dodge it). */
    private static void checkMethodFits(List<ProcurementMethod> methodList, String code, long estimateMinor) {
        List<ProcurementMethod> allowed = ProcurementRules.allowedMethods(methodList, estimateMinor, "KES");
        if (allowed.stream().noneMatch(m -> m.getCode().equals(code))) {
            throw new BusinessRuleException(code + " cannot be used for " + money(estimateMinor) + ". Allowed: "
                    + (allowed.isEmpty() ? "none (check Procurement settings)"
                            : allowed.stream().map(ProcurementMethod::getCode).collect(Collectors.joining(", "))));
        }
    }

    private static ProcurementMethod resolveMethod(List<ProcurementMethod> methodList, String text) {
        String n = PlanCsv.normalise(text);
        return methodList.stream()
                .filter(m -> PlanCsv.normalise(m.getCode()).equals(n) || PlanCsv.normalise(m.getName()).equals(n))
                .findFirst().orElse(null);
    }

    private List<ProcurementMethod> methodList(UUID tenantId) {
        return methods.findByTenantIdOrderBySortOrderAscCodeAsc(tenantId);
    }

    private boolean canEditLines(ProcurementPlan p, PlanDepartment d, User me) {
        return p.isDraft() && (isProcurement(me) || (me.getId().equals(d.getHeadId()) && d.isDraft()));
    }

    private void requireLineEditor(ProcurementPlan p, PlanDepartment d, User me) {
        p.requireDraft("change lines");
        if (!isProcurement(me) && !me.getId().equals(d.getHeadId())) {
            throw new ForbiddenActionException("The head of " + d.getName() + " and procurement edit its lines");
        }
        if (!isProcurement(me) && !d.isDraft()) {
            throw new BusinessRuleException(d.getName() + "'s lines were submitted; ask procurement to return them "
                    + "if they need to change");
        }
    }

    private void checkHead(UUID tenantId, UUID headId) {
        if (!activeUsers(tenantId, List.of(headId)).contains(headId)) {
            throw new BusinessRuleException("The department head must be an active user of this organisation");
        }
    }

    private void checkUniqueName(UUID tenantId, UUID planId, UUID exceptId, String name) {
        String n = name == null ? "" : name.trim();
        boolean taken = departments.findByTenantIdAndPlanIdOrderByNameAsc(tenantId, planId).stream()
                .anyMatch(d -> !d.getId().equals(exceptId) && d.getName().equalsIgnoreCase(n));
        if (taken) {
            throw new BusinessRuleException("The plan already has a department called " + n);
        }
    }

    private Set<UUID> activeUsers(UUID tenantId, Collection<UUID> ids) {
        Set<UUID> set = new HashSet<>(ids);
        set.remove(null);
        if (set.isEmpty()) {
            return Set.of();
        }
        return currentUser.byIds(tenantId, set).stream().filter(User::isActive).map(User::getId)
                .collect(Collectors.toSet());
    }

    private void notifyHead(UUID tenantId, ProcurementPlan p, PlanDepartment d, User me) {
        if (!d.getHeadId().equals(me.getId())) {
            notifications.notifyUsers(tenantId, List.of(d.getHeadId()), new Message("plan.department_assigned",
                    cap("Prepare " + d.getName() + "'s " + p.getFinancialYear() + " procurement plan", 200),
                    "Enter the department's planned purchases and submit them to procurement.", link(p)));
        }
    }

    private static Map<UUID, Long> totalsByDepartment(List<PlanLine> active) {
        return active.stream().collect(Collectors.groupingBy(PlanLine::getDepartmentId,
                Collectors.summingLong(PlanLine::getEstimateMinor)));
    }

    private AvailableLine available(PlanLine l, ProcurementPlan p, String dept, long committed) {
        long approved = l.getApprovedEstimateMinor() == null ? 0 : l.getApprovedEstimateMinor();
        return new AvailableLine(l.getId(), reference(p, l), p.getFinancialYear(), l.getDescription(), dept,
                l.getCategory(), l.getMethodCode(), l.getQuarter(), l.getProjectId(), approved, committed,
                PlanRules.balance(approved, committed));
    }

    static String reference(ProcurementPlan p, PlanLine l) {
        return "APP " + p.getFinancialYear() + "-" + String.format("%03d", l.getLineNo());
    }

    private static String lineAudit(ProcurementPlan p, PlanLine l, PlanDepartment d) {
        return JsonText.object("line", reference(p, l), "department", d.getName(), "description", l.getDescription(),
                "category", l.getCategory(), "estimateMinor", l.getEstimateMinor(), "method", l.getMethodCode(),
                "quarter", l.getQuarter(), "reserved", l.getReservedGroup());
    }

    private ProcurementPlan load(UUID tenantId, UUID planId) {
        return plans.findByTenantIdAndId(tenantId, planId)
                .orElseThrow(() -> new NotFoundException("Procurement plan not found"));
    }

    private ProcurementPlan lock(UUID tenantId, UUID planId) {
        return plans.lock(tenantId, planId).orElseThrow(() -> new NotFoundException("Procurement plan not found"));
    }

    private PlanDepartment department(UUID tenantId, UUID planId, UUID deptId) {
        return departments.findById(deptId)
                .filter(d -> d.getTenantId().equals(tenantId) && d.getPlanId().equals(planId))
                .orElseThrow(() -> new NotFoundException("Department not found in this plan"));
    }

    private PlanLine line(UUID tenantId, UUID planId, UUID lineId) {
        return lines.findByTenantIdAndId(tenantId, lineId).filter(l -> l.getPlanId().equals(planId))
                .orElseThrow(() -> new NotFoundException("Plan line not found"));
    }

    private User requireProcurement(String action) {
        User me = currentUser.require();
        Roles.requireAny(me, Roles.PROCUREMENT, action);
        return me;
    }

    private static boolean isProcurement(User me) {
        return me.getRoles().stream().anyMatch(Roles.PROCUREMENT::contains);
    }

    private Map<UUID, User> people(UUID tenantId, Collection<UUID> ids) {
        Set<UUID> set = new HashSet<>(ids);
        set.remove(null);
        return set.isEmpty() ? Map.of() : currentUser.byIds(tenantId, set).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private static Ref ref(Map<UUID, User> people, UUID id) {
        if (id == null) {
            return null;
        }
        User u = people.get(id);
        return new Ref(id, u == null ? "Unknown user" : u.getDisplayName());
    }

    static String money(long minor) {
        NumberFormat f = NumberFormat.getNumberInstance(Locale.ENGLISH);
        f.setMinimumFractionDigits(2);
        f.setMaximumFractionDigits(2);
        return "KES " + f.format(BigDecimal.valueOf(minor, 2));
    }

    private static String cap(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String link(ProcurementPlan p) {
        return "/procurement-plans/" + p.getId();
    }

    private Instant now() {
        return Instant.now(clock);
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(LOCAL_ZONE));
    }
}
