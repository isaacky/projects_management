package com.pmplatform.execution;

import com.pmplatform.contracts.ContractService;
import com.pmplatform.contracts.ContractStatus;
import com.pmplatform.contracts.api.ContractDtos.ContractSummary;
import com.pmplatform.execution.ExecutionRules.RiskLevel;
import com.pmplatform.execution.api.ExecutionDtos.Figures;
import com.pmplatform.execution.api.ExecutionDtos.IssueRequest;
import com.pmplatform.execution.api.ExecutionDtos.IssueView;
import com.pmplatform.execution.api.ExecutionDtos.ProjectHealth;
import com.pmplatform.execution.api.ExecutionDtos.ReportRequest;
import com.pmplatform.execution.api.ExecutionDtos.ReportView;
import com.pmplatform.execution.api.ExecutionDtos.RiskRequest;
import com.pmplatform.execution.api.ExecutionDtos.RiskView;
import com.pmplatform.execution.api.ExecutionDtos.Suggested;
import com.pmplatform.planning.PlanningService;
import com.pmplatform.planning.ScheduleEngine.Result;
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
import com.pmplatform.procurement.tender.api.TenderDtos.Ref;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Running a project: the risk register, the issue log, periodic status reports (figures frozen
 * on submission, reviewed by the PMO) and the portfolio health view. Anyone may raise a risk or
 * an issue; project managers, the PMO, the owner and the person who raised it may update it.
 */
@Service
public class ExecutionService {

    static final ZoneId LOCAL_ZONE = ZoneId.of("Africa/Nairobi");

    private final RiskRepository risks;
    private final IssueRepository issues;
    private final StatusReportRepository reports;
    private final ProjectService projects;
    private final PlanningService planning;
    private final ContractService contracts;
    private final CurrentUserService currentUser;
    private final NotificationService notifications;
    private final AuditService audit;
    private final Clock clock;

    public ExecutionService(RiskRepository risks, IssueRepository issues, StatusReportRepository reports,
            ProjectService projects, PlanningService planning, ContractService contracts,
            CurrentUserService currentUser, NotificationService notifications, AuditService audit, Clock clock) {
        this.risks = risks;
        this.issues = issues;
        this.reports = reports;
        this.projects = projects;
        this.planning = planning;
        this.contracts = contracts;
        this.currentUser = currentUser;
        this.notifications = notifications;
        this.audit = audit;
        this.clock = clock;
    }

    // ================================================================== risks

    @Transactional(readOnly = true)
    public List<RiskView> risks(UUID tenantId, UUID projectId) {
        projects.get(tenantId, projectId);
        User me = currentUser.require();
        List<Risk> rows = risks.findByTenantIdAndProjectIdOrderByNumberAsc(tenantId, projectId);
        Map<UUID, User> people = people(tenantId, rows.stream().flatMap(r -> java.util.stream.Stream.of(r.getOwnerId(),
                r.getCreatedBy())).toList());
        return rows.stream().map(r -> riskView(r, people, me)).toList();
    }

    @Transactional
    public RiskView createRisk(UUID tenantId, UUID projectId, RiskRequest req) {
        projects.get(tenantId, projectId);
        User me = currentUser.require();
        checkOwner(tenantId, req.ownerId());
        Instant now = now();
        int number = risks.findByTenantIdAndProjectIdOrderByNumberAsc(tenantId, projectId).stream()
                .mapToInt(Risk::getNumber).max().orElse(0) + 1;
        Risk r = new Risk(tenantId, projectId, number, me.getId(), now);
        r.update(req.title(), req.description(), req.category(), req.probability(), req.impact(), req.response(),
                req.mitigation(), req.ownerId(), req.dueDate(), now);
        risks.save(r);
        audit.record(tenantId, "risk.raised", "Project", projectId,
                JsonText.object("risk", "R" + number, "title", r.getTitle(), "level", r.level(),
                        "probability", r.getProbability(), "impact", r.getImpact()));
        notifyOwner(tenantId, r.getOwnerId(), me, "risk.assigned", "Risk R" + number + " assigned to you",
                r.getTitle(), projectId, "risks");
        return riskView(r, people(tenantId, List.of(r.getOwnerId(), r.getCreatedBy())), me);
    }

    @Transactional
    public RiskView updateRisk(UUID tenantId, UUID projectId, UUID riskId, RiskRequest req) {
        User me = currentUser.require();
        Risk r = risk(tenantId, projectId, riskId);
        requireRiskEditor(r, me);
        checkOwner(tenantId, req.ownerId());
        UUID before = r.getOwnerId();
        RiskLevel levelBefore = r.level();
        r.update(req.title(), req.description(), req.category(), req.probability(), req.impact(), req.response(),
                req.mitigation(), req.ownerId(), req.dueDate(), now());
        audit.record(tenantId, "risk.updated", "Project", projectId,
                JsonText.object("risk", "R" + r.getNumber(), "level", r.level(), "previousLevel", levelBefore,
                        "probability", r.getProbability(), "impact", r.getImpact(), "response", r.getResponse()));
        if (r.getOwnerId() != null && !r.getOwnerId().equals(before)) {
            notifyOwner(tenantId, r.getOwnerId(), me, "risk.assigned", "Risk R" + r.getNumber() + " assigned to you",
                    r.getTitle(), projectId, "risks");
        }
        return riskView(r, people(tenantId, List.of(r.getOwnerId(), r.getCreatedBy())), me);
    }

    @Transactional
    public RiskView closeRisk(UUID tenantId, UUID projectId, UUID riskId, String note, boolean reopen) {
        User me = currentUser.require();
        Risk r = risk(tenantId, projectId, riskId);
        requireRiskEditor(r, me);
        if (reopen) {
            r.reopen(now());
        } else {
            r.close(note, now());
        }
        audit.record(tenantId, reopen ? "risk.reopened" : "risk.closed", "Project", projectId,
                JsonText.object("risk", "R" + r.getNumber(), "note", note));
        return riskView(r, people(tenantId, List.of(r.getOwnerId(), r.getCreatedBy())), me);
    }

    // ================================================================== issues

    @Transactional(readOnly = true)
    public List<IssueView> issues(UUID tenantId, UUID projectId) {
        projects.get(tenantId, projectId);
        User me = currentUser.require();
        List<Issue> rows = issues.findByTenantIdAndProjectIdOrderByNumberAsc(tenantId, projectId);
        Map<UUID, Integer> riskNumbers = risks.findByTenantIdAndProjectIdOrderByNumberAsc(tenantId, projectId).stream()
                .collect(Collectors.toMap(Risk::getId, Risk::getNumber));
        Map<UUID, User> people = people(tenantId, rows.stream().flatMap(i -> java.util.stream.Stream.of(i.getOwnerId(),
                i.getCreatedBy())).toList());
        return rows.stream().map(i -> issueView(i, riskNumbers, people, me)).toList();
    }

    @Transactional
    public IssueView saveIssue(UUID tenantId, UUID projectId, UUID issueId, IssueRequest req) {
        projects.get(tenantId, projectId);
        User me = currentUser.require();
        checkOwner(tenantId, req.ownerId());
        if (req.riskId() != null) {
            risk(tenantId, projectId, req.riskId());
        }
        Instant now = now();
        Issue i;
        boolean created = issueId == null;
        UUID ownerBefore = null;
        if (created) {
            int number = issues.findByTenantIdAndProjectIdOrderByNumberAsc(tenantId, projectId).stream()
                    .mapToInt(Issue::getNumber).max().orElse(0) + 1;
            i = new Issue(tenantId, projectId, number, me.getId(), now);
        } else {
            i = issues.findByTenantIdAndProjectIdAndId(tenantId, projectId, issueId)
                    .orElseThrow(() -> new NotFoundException("Issue not found"));
            if (!isEditor(me) && !me.getId().equals(i.getOwnerId()) && !me.getId().equals(i.getCreatedBy())) {
                throw new ForbiddenActionException("Project managers, the PMO, the owner and whoever raised the issue "
                        + "update it");
            }
            ownerBefore = i.getOwnerId();
        }
        IssueStatus status = req.status() == null ? (created ? IssueStatus.OPEN : i.getStatus()) : req.status();
        i.update(req.title(), req.description(), req.priority(), status, req.ownerId(), req.dueDate(), req.resolution(),
                req.riskId(), now);
        issues.save(i);
        audit.record(tenantId, created ? "issue.raised" : "issue.updated", "Project", projectId,
                JsonText.object("issue", "I" + i.getNumber(), "title", i.getTitle(), "priority", i.getPriority(),
                        "status", i.getStatus(), "resolution", i.getResolution()));
        if (i.getOwnerId() != null && !i.getOwnerId().equals(ownerBefore)) {
            notifyOwner(tenantId, i.getOwnerId(), me, "issue.assigned", "Issue I" + i.getNumber() + " assigned to you",
                    i.getTitle(), projectId, "issues");
        }
        Map<UUID, Integer> riskNumbers = risks.findByTenantIdAndProjectIdOrderByNumberAsc(tenantId, projectId).stream()
                .collect(Collectors.toMap(Risk::getId, Risk::getNumber));
        return issueView(i, riskNumbers, people(tenantId, List.of(i.getOwnerId(), i.getCreatedBy())), me);
    }

    // ================================================================== status reports

    @Transactional(readOnly = true)
    public List<ReportView> reports(UUID tenantId, UUID projectId) {
        Project p = projects.get(tenantId, projectId);
        User me = currentUser.require();
        List<StatusReport> rows = reports.findByTenantIdAndProjectIdOrderByPeriodEndDesc(tenantId, projectId);
        Figures live = rows.stream().anyMatch(r -> r.getStatus() == ReportStatus.DRAFT) ? figures(tenantId, p) : null;
        return rows.stream().map(r -> reportView(r, live, me)).toList();
    }

    @Transactional
    public ReportView createReport(UUID tenantId, UUID projectId, LocalDate periodEnd) {
        User me = requireEditor("write status reports");
        Project p = projects.get(tenantId, projectId);
        if (reports.existsByProjectIdAndPeriodEnd(projectId, periodEnd)) {
            throw new BusinessRuleException("There is already a report for the period ending " + periodEnd);
        }
        Instant now = now();
        Figures f = figures(tenantId, p);
        Suggested s = suggest(f);
        StatusReport r = new StatusReport(tenantId, projectId, periodEnd, me.getId(), now);
        r.edit(periodEnd, s.overall(), s.schedule(), s.cost(), Rag.GREEN, s.risk(), "To be written", null, null, null, now);
        reports.save(r);
        audit.record(tenantId, "report.created", "Project", projectId, JsonText.object("periodEnd", periodEnd));
        return reportView(r, f, me);
    }

    @Transactional
    public ReportView editReport(UUID tenantId, UUID projectId, UUID reportId, ReportRequest req) {
        User me = requireEditor("write status reports");
        Project p = projects.get(tenantId, projectId);
        StatusReport r = report(tenantId, projectId, reportId);
        if (!req.periodEnd().equals(r.getPeriodEnd()) && reports.existsByProjectIdAndPeriodEnd(projectId, req.periodEnd())) {
            throw new BusinessRuleException("There is already a report for the period ending " + req.periodEnd());
        }
        r.edit(req.periodEnd(), req.overall(), req.schedule(), req.cost(), req.scope(), req.risk(), req.summary(),
                req.achievements(), req.nextSteps(), req.escalations(), now());
        audit.record(tenantId, "report.edited", "Project", projectId,
                JsonText.object("periodEnd", r.getPeriodEnd(), "overall", r.getRagOverall()));
        return reportView(r, figures(tenantId, p), me);
    }

    @Transactional
    public ReportView submitReport(UUID tenantId, UUID projectId, UUID reportId) {
        User me = requireEditor("submit status reports");
        Project p = projects.get(tenantId, projectId);
        StatusReport r = report(tenantId, projectId, reportId);
        if ("To be written".equals(r.getSummary())) {
            throw new BusinessRuleException("Write the summary before submitting");
        }
        Figures f = figures(tenantId, p);
        r.submit(new StatusReport.Figures(f.plannedPct(), f.earnedPct(), f.spi(), f.cpi(), f.baselineFinish(),
                f.forecastFinish(), f.budgetMinor(), f.committedMinor(), f.slipDays(), f.certifiedMinor(),
                f.openRisks(), f.highRisks(), f.criticalRisks(), f.openIssues()), me.getId(), now());
        audit.record(tenantId, "report.submitted", "Project", projectId,
                JsonText.object("periodEnd", r.getPeriodEnd(), "overall", r.getRagOverall(), "schedule",
                        r.getRagSchedule(), "cost", r.getRagCost(), "spi", f.spi(), "cpi", f.cpi()));
        notifications.notifyRole(tenantId, Roles.PMO_LEAD, new Message("report.submitted",
                p.getCode() + " status report (" + r.getRagOverall().name().toLowerCase() + ")",
                "Period ending " + r.getPeriodEnd() + ". " + r.getSummary(), "/projects/" + projectId + "?tab=reports"));
        return reportView(r, null, me);
    }

    @Transactional
    public ReportView reviewReport(UUID tenantId, UUID projectId, UUID reportId, String comment) {
        User me = currentUser.require();
        Roles.requireAny(me, Set.of(Roles.PMO_LEAD), "review status reports");
        projects.get(tenantId, projectId);
        StatusReport r = report(tenantId, projectId, reportId);
        if (me.getId().equals(r.getSubmittedBy())) {
            throw new ForbiddenActionException("Another PMO lead reviews a report you submitted");
        }
        r.review(me.getId(), comment, now());
        audit.record(tenantId, "report.reviewed", "Project", projectId,
                JsonText.object("periodEnd", r.getPeriodEnd(), "comment", comment));
        notifications.notifyUsers(tenantId, List.of(r.getSubmittedBy()), new Message("report.reviewed",
                "Status report reviewed", comment == null ? "No comment." : comment,
                "/projects/" + projectId + "?tab=reports"));
        return reportView(r, null, me);
    }

    // ================================================================== portfolio

    @Transactional(readOnly = true)
    public List<ProjectHealth> portfolioHealth(UUID tenantId) {
        currentUser.require();
        List<Project> all = projects.list(tenantId, null);
        Map<UUID, List<Risk>> openRisks = risks.findByTenantIdAndStatus(tenantId, RiskStatus.OPEN).stream()
                .collect(Collectors.groupingBy(Risk::getProjectId));
        Map<UUID, List<Issue>> openIssues = issues.findByTenantId(tenantId).stream().filter(Issue::isOpen)
                .collect(Collectors.groupingBy(Issue::getProjectId));
        Map<UUID, StatusReport> latest = reports.findByTenantIdAndStatusNot(tenantId, ReportStatus.DRAFT).stream()
                .collect(Collectors.toMap(StatusReport::getProjectId, Function.identity(),
                        (a, b) -> a.getPeriodEnd().isAfter(b.getPeriodEnd()) ? a : b));
        return all.stream().map(p -> {
            Figures f = figures(tenantId, p, openRisks.getOrDefault(p.getId(), List.of()),
                    openIssues.getOrDefault(p.getId(), List.of()));
            StatusReport last = latest.get(p.getId());
            return new ProjectHealth(p.getId(), p.getCode(), p.getName(), p.getPhase(), p.getCurrency(),
                    p.getBudgetMinor(), f.committedMinor(), f.certifiedMinor(), f.plannedPct(), f.earnedPct(), f.spi(),
                    f.cpi(), f.baselineFinish(), f.forecastFinish(), f.slipDays(), f.openRisks(), f.highRisks(),
                    f.openIssues(), last == null ? null : last.getRagOverall(),
                    last == null ? null : last.getPeriodEnd(), suggest(f).overall());
        }).sorted(Comparator.comparing(ProjectHealth::code)).toList();
    }

    // ================================================================== figures

    private Figures figures(UUID tenantId, Project p) {
        return figures(tenantId, p,
                risks.findByTenantIdAndProjectIdOrderByNumberAsc(tenantId, p.getId()).stream()
                        .filter(r -> r.getStatus() == RiskStatus.OPEN).toList(),
                issues.findByTenantIdAndProjectIdOrderByNumberAsc(tenantId, p.getId()).stream()
                        .filter(Issue::isOpen).toList());
    }

    private Figures figures(UUID tenantId, Project p, Collection<Risk> open, Collection<Issue> openIssues) {
        Optional<Result> sched = planning.compute(tenantId, p.getId());
        List<ContractSummary> cs = contracts.list(tenantId, p.getId(), null).stream()
                .filter(c -> c.status() == ContractStatus.ACTIVE || c.status() == ContractStatus.COMPLETED
                        || c.status() == ContractStatus.TERMINATED)
                .filter(c -> c.currency().equals(p.getCurrency())).toList();
        long committed = cs.stream().mapToLong(ContractSummary::currentValueMinor).sum();
        long certified = cs.stream().mapToLong(ContractSummary::certifiedMinor).sum();
        var earned = sched.map(Result::earnedPct).orElse(null);
        int high = (int) open.stream().filter(r -> r.level() == RiskLevel.HIGH || r.level() == RiskLevel.CRITICAL)
                .count();
        int critical = (int) open.stream().filter(r -> r.level() == RiskLevel.CRITICAL).count();
        return new Figures(sched.map(Result::plannedPct).orElse(null), earned, sched.map(Result::spi).orElse(null),
                ExecutionRules.cpi(earned, p.getBudgetMinor(), certified),
                sched.map(Result::baselineFinish).orElse(null), sched.map(Result::forecastFinish).orElse(null),
                sched.map(Result::slipDays).orElse(null), p.getBudgetMinor(), committed, certified, open.size(), high,
                critical, openIssues.size(), false);
    }

    private static Suggested suggest(Figures f) {
        Rag schedule = ExecutionRules.scheduleRag(f.spi(), f.slipDays() == null ? 0 : f.slipDays());
        Rag cost = ExecutionRules.costRag(f.cpi(), f.committedMinor(), f.budgetMinor());
        Rag risk = ExecutionRules.riskRag(f.criticalRisks(), f.highRisks() - f.criticalRisks());
        return new Suggested(ExecutionRules.worst(schedule, cost, risk), schedule, cost, risk);
    }

    // ================================================================== helpers and views

    private Risk risk(UUID tenantId, UUID projectId, UUID id) {
        return risks.findByTenantIdAndProjectIdAndId(tenantId, projectId, id)
                .orElseThrow(() -> new NotFoundException("Risk not found"));
    }

    private StatusReport report(UUID tenantId, UUID projectId, UUID id) {
        return reports.findByTenantIdAndProjectIdAndId(tenantId, projectId, id)
                .orElseThrow(() -> new NotFoundException("Status report not found"));
    }

    private void requireRiskEditor(Risk r, User me) {
        if (!isEditor(me) && !me.getId().equals(r.getOwnerId()) && !me.getId().equals(r.getCreatedBy())) {
            throw new ForbiddenActionException("Project managers, the PMO, the owner and whoever raised the risk update it");
        }
    }

    private User requireEditor(String action) {
        User me = currentUser.require();
        Roles.requireAny(me, Roles.PLAN_EDITORS, action);
        return me;
    }

    private static boolean isEditor(User me) {
        return me.getRoles().stream().anyMatch(Roles.PLAN_EDITORS::contains);
    }

    private void checkOwner(UUID tenantId, UUID ownerId) {
        if (ownerId == null) {
            return;
        }
        User u = people(tenantId, List.of(ownerId)).get(ownerId);
        if (u == null || !u.isActive()) {
            throw new BusinessRuleException("The owner must be an active user of this organisation");
        }
    }

    private void notifyOwner(UUID tenantId, UUID ownerId, User me, String type, String title, String body,
            UUID projectId, String tab) {
        if (ownerId != null && !ownerId.equals(me.getId())) {
            notifications.notifyUsers(tenantId, List.of(ownerId),
                    new Message(type, title, body, "/projects/" + projectId + "?tab=" + tab));
        }
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

    private Instant now() {
        return Instant.now(clock);
    }

    private LocalDate today() {
        return LocalDate.ofInstant(now(), LOCAL_ZONE);
    }

    private RiskView riskView(Risk r, Map<UUID, User> people, User me) {
        boolean canEdit = isEditor(me) || me.getId().equals(r.getOwnerId()) || me.getId().equals(r.getCreatedBy());
        return new RiskView(r.getId(), r.getNumber(), r.getTitle(), r.getDescription(), r.getCategory(),
                r.getProbability(), r.getImpact(), r.getProbability() * r.getImpact(), r.level(), r.getResponse(),
                r.getMitigation(), ref(people, r.getOwnerId()), r.getDueDate(),
                r.getStatus() == RiskStatus.OPEN && r.getDueDate() != null && r.getDueDate().isBefore(today()),
                r.getStatus(), r.getClosedNote(), r.getClosedAt(), ref(people, r.getCreatedBy()), r.getCreatedAt(),
                canEdit);
    }

    private IssueView issueView(Issue i, Map<UUID, Integer> riskNumbers, Map<UUID, User> people, User me) {
        boolean canEdit = isEditor(me) || me.getId().equals(i.getOwnerId()) || me.getId().equals(i.getCreatedBy());
        return new IssueView(i.getId(), i.getNumber(), i.getTitle(), i.getDescription(), i.getPriority(), i.getStatus(),
                ref(people, i.getOwnerId()), i.getDueDate(),
                i.isOpen() && i.getDueDate() != null && i.getDueDate().isBefore(today()), i.getResolution(),
                i.getRiskId(), i.getRiskId() == null ? null : riskNumbers.get(i.getRiskId()),
                ref(people, i.getCreatedBy()), i.getCreatedAt(), canEdit);
    }

    private ReportView reportView(StatusReport r, Figures live, User me) {
        Map<UUID, User> people = people(r.getTenantId(), java.util.Arrays.asList(r.getCreatedBy(), r.getSubmittedBy(),
                r.getReviewedBy()));
        Figures f = r.getStatus() == ReportStatus.DRAFT ? live
                : new Figures(r.getPlannedPct(), r.getEarnedPct(), r.getSpi(), r.getCpi(), r.getBaselineFinish(),
                        r.getForecastFinish(), r.getSlipDays(),
                        nz(r.getBudgetMinor()), nz(r.getCommittedMinor()), nz(r.getCertifiedMinor()),
                        nzi(r.getOpenRisks()), nzi(r.getHighRisks()), nzi(r.getCriticalRisks()), nzi(r.getOpenIssues()),
                        true);
        boolean editor = isEditor(me);
        return new ReportView(r.getId(), r.getPeriodEnd(), r.getStatus(), r.getRagOverall(), r.getRagSchedule(),
                r.getRagCost(), r.getRagScope(), r.getRagRisk(), r.getSummary(), r.getAchievements(), r.getNextSteps(),
                r.getEscalations(), f, f == null ? null : suggest(f), ref(people, r.getCreatedBy()), r.getCreatedAt(),
                ref(people, r.getSubmittedBy()), r.getSubmittedAt(), ref(people, r.getReviewedBy()), r.getReviewedAt(),
                r.getReviewComment(), editor && r.getStatus() == ReportStatus.DRAFT,
                editor && r.getStatus() == ReportStatus.DRAFT,
                me.hasRole(Roles.PMO_LEAD) && r.getStatus() == ReportStatus.SUBMITTED);
    }

    private static long nz(Long v) {
        return v == null ? 0 : v;
    }

    private static int nzi(Integer v) {
        return v == null ? 0 : v;
    }
}
