package com.pmplatform.benefits;

import com.pmplatform.benefits.api.BenefitDtos.AssessmentInput;
import com.pmplatform.benefits.api.BenefitDtos.AssessmentView;
import com.pmplatform.benefits.api.BenefitDtos.BenefitRequest;
import com.pmplatform.benefits.api.BenefitDtos.BenefitView;
import com.pmplatform.benefits.api.BenefitDtos.FindingsRequest;
import com.pmplatform.benefits.api.BenefitDtos.MeasurementRequest;
import com.pmplatform.benefits.api.BenefitDtos.MeasurementView;
import com.pmplatform.benefits.api.BenefitDtos.Permissions;
import com.pmplatform.benefits.api.BenefitDtos.PortfolioView;
import com.pmplatform.benefits.api.BenefitDtos.ProjectBenefitsView;
import com.pmplatform.benefits.api.BenefitDtos.ProjectRef;
import com.pmplatform.benefits.api.BenefitDtos.ReviewRequest;
import com.pmplatform.benefits.api.BenefitDtos.ReviewView;
import com.pmplatform.benefits.api.BenefitDtos.Summary;
import com.pmplatform.closeout.CloseoutService;
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
import com.pmplatform.portfolio.ProjectStatus;
import com.pmplatform.procurement.tender.api.TenderDtos.Ref;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Benefits realisation: the register, measurements against targets, and post-implementation
 * reviews. Project managers and the PMO keep the register and plan reviews; each benefit's owner
 * records its measurements; the review lead writes up and completes the review.
 */
@Service
public class BenefitService {

    static final ZoneId LOCAL_ZONE = ZoneId.of("Africa/Nairobi");
    /** Review leads are reminded this many days before the review is due. */
    static final int REVIEW_REMINDER_DAYS = 7;

    private final BenefitRepository benefits;
    private final MeasurementRepository measurements;
    private final ReviewRepository reviews;
    private final ReviewAssessmentRepository assessments;
    private final ProjectService projects;
    private final CloseoutService closeout;
    private final CurrentUserService currentUser;
    private final NotificationService notifications;
    private final AuditService audit;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public BenefitService(BenefitRepository benefits, MeasurementRepository measurements, ReviewRepository reviews,
            ReviewAssessmentRepository assessments, ProjectService projects, CloseoutService closeout,
            CurrentUserService currentUser, NotificationService notifications, AuditService audit, JdbcTemplate jdbc,
            Clock clock) {
        this.benefits = benefits;
        this.measurements = measurements;
        this.reviews = reviews;
        this.assessments = assessments;
        this.projects = projects;
        this.closeout = closeout;
        this.currentUser = currentUser;
        this.notifications = notifications;
        this.audit = audit;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    // ================================================================== queries

    @Transactional(readOnly = true)
    public ProjectBenefitsView forProject(UUID tenantId, UUID projectId) {
        Project p = projects.get(tenantId, projectId);
        return projectView(p, currentUser.require());
    }

    /** Every benefit in the organisation, with the reviews coming up, for the portfolio page and dashboard. */
    @Transactional(readOnly = true)
    public PortfolioView portfolio(UUID tenantId) {
        User me = currentUser.require();
        List<Benefit> all = benefits.findByTenantIdOrderByProjectIdAscNumberAsc(tenantId);
        List<Review> planned = reviews.findByTenantIdOrderByDueDateAsc(tenantId).stream()
                .filter(r -> r.getStatus() == ReviewStatus.PLANNED).toList();
        Set<UUID> projectIds = new HashSet<>();
        all.forEach(b -> projectIds.add(b.getProjectId()));
        planned.forEach(r -> projectIds.add(r.getProjectId()));
        Map<UUID, Project> projectMap = projects.byIds(tenantId, projectIds);
        Ctx ctx = context(tenantId, all, planned, me);
        List<BenefitView> views = all.stream()
                .sorted(Comparator.comparing((Benefit b) -> code(projectMap, b.getProjectId()))
                        .thenComparingInt(Benefit::getNumber))
                .map(b -> benefitView(b, projectMap.get(b.getProjectId()), ctx, me)).toList();
        List<ReviewView> upcoming = planned.stream()
                .map(r -> reviewView(r, projectMap.get(r.getProjectId()), ctx, me)).toList();
        return new PortfolioView(summary(views, planned), views, upcoming);
    }

    // ================================================================== benefits

    @Transactional
    public ProjectBenefitsView addBenefit(UUID tenantId, UUID projectId, BenefitRequest req) {
        User me = requireEditor("add benefits");
        Project p = projects.get(tenantId, projectId);
        requireUsable(p);
        checkUser(tenantId, req.ownerId(), "The benefit owner");
        lockProject(projectId);
        int number = benefits.findByTenantIdAndProjectIdOrderByNumberAsc(tenantId, projectId).stream()
                .mapToInt(Benefit::getNumber).max().orElse(0) + 1;
        Instant now = now();
        Benefit b = new Benefit(tenantId, projectId, number, me.getId(), now);
        b.define(definition(req), now);
        benefits.save(b);
        audit.record(tenantId, "benefit.added", "Project", projectId, benefitAudit(b));
        notifyOwner(tenantId, p, b, me);
        return projectView(p, me);
    }

    @Transactional
    public ProjectBenefitsView editBenefit(UUID tenantId, UUID projectId, UUID benefitId, BenefitRequest req) {
        User me = requireEditor("change benefits");
        Project p = projects.get(tenantId, projectId);
        Benefit b = lockedBenefit(tenantId, projectId, benefitId);
        checkVersion(req.version(), b.getVersion(), "B" + b.getNumber());
        checkUser(tenantId, req.ownerId(), "The benefit owner");
        measurements.findByTenantIdAndBenefitIdInOrderByMeasuredOnAsc(tenantId, List.of(b.getId())).stream()
                .findFirst().filter(first -> first.getMeasuredOn().isBefore(req.baselineDate()))
                .ifPresent(first -> {
                    throw new BusinessRuleException("B" + b.getNumber() + " has a reading on " + first.getMeasuredOn()
                            + "; the baseline date cannot be later than that");
                });
        UUID oldOwner = b.getOwnerId();
        b.define(definition(req), now());
        audit.record(tenantId, "benefit.changed", "Project", projectId, benefitAudit(b));
        if (!oldOwner.equals(b.getOwnerId())) {
            notifyOwner(tenantId, p, b, me);
        }
        return projectView(p, me);
    }

    @Transactional
    public ProjectBenefitsView closeBenefit(UUID tenantId, UUID projectId, UUID benefitId, BenefitStatus outcome,
            String note) {
        User me = requireEditor("close benefits");
        Project p = projects.get(tenantId, projectId);
        Benefit b = lockedBenefit(tenantId, projectId, benefitId);
        b.close(outcome, note, me.getId(), now());
        audit.record(tenantId, "benefit.closed", "Project", projectId,
                JsonText.object("benefit", "B" + b.getNumber(), "title", b.getTitle(), "outcome", outcome, "note", note));
        return projectView(p, me);
    }

    @Transactional
    public ProjectBenefitsView reopenBenefit(UUID tenantId, UUID projectId, UUID benefitId) {
        User me = currentUser.require();
        Roles.requireAny(me, Set.of(Roles.PMO_LEAD), "reopen a closed benefit");
        Project p = projects.get(tenantId, projectId);
        Benefit b = lockedBenefit(tenantId, projectId, benefitId);
        b.reopen(now());
        audit.record(tenantId, "benefit.reopened", "Project", projectId,
                JsonText.object("benefit", "B" + b.getNumber(), "title", b.getTitle()));
        return projectView(p, me);
    }

    // ================================================================== measurements

    @Transactional
    public ProjectBenefitsView recordMeasurement(UUID tenantId, UUID projectId, UUID benefitId,
            MeasurementRequest req) {
        User me = currentUser.require();
        Project p = projects.get(tenantId, projectId);
        Benefit b = lockedBenefit(tenantId, projectId, benefitId);
        if (!isEditor(me) && !me.getId().equals(b.getOwnerId())) {
            throw new ForbiddenActionException("The benefit owner, the project manager or the PMO records measurements");
        }
        b.requireActive("measured");
        if (measurements.existsByBenefitIdAndMeasuredOn(b.getId(), req.measuredOn())) {
            throw new BusinessRuleException("B" + b.getNumber() + " already has a measurement on " + req.measuredOn()
                    + "; delete it first to correct it");
        }
        Instant now = now();
        Measurement m = new Measurement(tenantId, b.getId(), now);
        m.record(b, req.measuredOn(), req.value(), req.source(), req.note(), today(), me.getId(), now);
        measurements.save(m);
        BigDecimal progress = BenefitRules.progressPct(b.getBaselineValue(), b.getTargetValue(), m.getValue());
        audit.record(tenantId, "benefit.measured", "Project", projectId,
                JsonText.object("benefit", "B" + b.getNumber(), "measuredOn", m.getMeasuredOn(), "value", m.getValue(),
                        "progressPct", progress, "source", m.getSource()));
        return projectView(p, me);
    }

    @Transactional
    public ProjectBenefitsView deleteMeasurement(UUID tenantId, UUID projectId, UUID benefitId, UUID measurementId) {
        User me = currentUser.require();
        Project p = projects.get(tenantId, projectId);
        Benefit b = lockedBenefit(tenantId, projectId, benefitId);
        Measurement m = measurements.findById(measurementId)
                .filter(x -> x.getTenantId().equals(tenantId) && x.getBenefitId().equals(b.getId()))
                .orElseThrow(() -> new NotFoundException("Measurement not found"));
        if (!isEditor(me) && !me.getId().equals(m.getRecordedBy())) {
            throw new ForbiddenActionException("Whoever recorded the measurement, the project manager or the PMO "
                    + "deletes it");
        }
        b.requireActive("changed");
        measurements.delete(m);
        audit.record(tenantId, "benefit.measurement_deleted", "Project", projectId,
                JsonText.object("benefit", "B" + b.getNumber(), "measuredOn", m.getMeasuredOn(), "value", m.getValue()));
        return projectView(p, me);
    }

    // ================================================================== reviews

    @Transactional
    public ProjectBenefitsView planReview(UUID tenantId, UUID projectId, ReviewRequest req) {
        User me = requireEditor("plan post-implementation reviews");
        Project p = projects.get(tenantId, projectId);
        requireUsable(p);
        checkUser(tenantId, req.leadId(), "The review lead");
        Instant now = now();
        Review r = new Review(tenantId, projectId, me.getId(), now);
        r.plan(req.title(), req.dueDate(), req.leadId(), now);
        reviews.save(r);
        audit.record(tenantId, "benefit.review_planned", "Project", projectId,
                JsonText.object("title", r.getTitle(), "dueDate", r.getDueDate(), "lead", r.getLeadId()));
        notifyLead(tenantId, p, r, me);
        return projectView(p, me);
    }

    /** The usual pair: reviews 6 and 12 months after the accepted handover. */
    @Transactional
    public ProjectBenefitsView planStandardReviews(UUID tenantId, UUID projectId, UUID leadId) {
        User me = requireEditor("plan post-implementation reviews");
        Project p = projects.get(tenantId, projectId);
        requireUsable(p);
        LocalDate handover = closeout.acceptedHandoverDate(tenantId, projectId)
                .orElseThrow(() -> new BusinessRuleException("The standard reviews are timed from the accepted "
                        + "handover; plan reviews by date until then"));
        checkUser(tenantId, leadId, "The review lead");
        lockProject(projectId); // a double click must not plan the pair twice
        Instant now = now();
        List<Review> existing = reviews.findByTenantIdAndProjectIdOrderByDueDateAsc(tenantId, projectId);
        int added = 0;
        for (int months : List.of(6, 12)) {
            String title = months + "-month post-implementation review";
            if (existing.stream().anyMatch(x -> x.getStatus() != ReviewStatus.CANCELLED && title.equals(x.getTitle()))) {
                continue;
            }
            Review r = new Review(tenantId, projectId, me.getId(), now);
            r.plan(title, handover.plusMonths(months), leadId, now);
            reviews.save(r);
            added++;
        }
        if (added == 0) {
            throw new BusinessRuleException("The 6- and 12-month reviews are already planned");
        }
        audit.record(tenantId, "benefit.reviews_planned", "Project", projectId,
                JsonText.object("handoverDate", handover, "reviews", added, "lead", leadId));
        if (!leadId.equals(me.getId())) {
            notifications.notifyUsers(tenantId, List.of(leadId), new Message("benefit.review_planned",
                    cap(p.getCode() + ": you lead the post-implementation reviews", 200),
                    "Due " + handover.plusMonths(6) + " and " + handover.plusMonths(12) + ".", link(p)));
        }
        return projectView(p, me);
    }

    @Transactional
    public ProjectBenefitsView editReview(UUID tenantId, UUID projectId, UUID reviewId, ReviewRequest req) {
        User me = requireEditor("change post-implementation reviews");
        Project p = projects.get(tenantId, projectId);
        Review r = review(tenantId, projectId, reviewId);
        checkVersion(req.version(), r.getVersion(), r.getTitle());
        checkUser(tenantId, req.leadId(), "The review lead");
        UUID oldLead = r.getLeadId();
        r.plan(req.title(), req.dueDate(), req.leadId(), now());
        audit.record(tenantId, "benefit.review_changed", "Project", projectId,
                JsonText.object("title", r.getTitle(), "dueDate", r.getDueDate(), "lead", r.getLeadId()));
        if (!oldLead.equals(r.getLeadId())) {
            notifyLead(tenantId, p, r, me);
        }
        return projectView(p, me);
    }

    /** Saves the findings and each benefit's assessment as a draft (or, with complete, completes the review). */
    @Transactional
    public ProjectBenefitsView saveFindings(UUID tenantId, UUID projectId, UUID reviewId, FindingsRequest req,
            boolean complete) {
        User me = currentUser.require();
        Project p = projects.get(tenantId, projectId);
        Review r = review(tenantId, projectId, reviewId);
        if (!isEditor(me) && !me.getId().equals(r.getLeadId())) {
            throw new ForbiddenActionException("The review lead, the project manager or the PMO writes up the review");
        }
        Instant now = now();
        r.findings(req.conductedOn(), req.objectivesMet(), req.summary(), req.recommendations(), today(), now);
        List<Benefit> list = benefits.findByTenantIdAndProjectIdOrderByNumberAsc(tenantId, projectId);
        Map<UUID, Benefit> byId = list.stream().collect(Collectors.toMap(Benefit::getId, Function.identity()));
        Map<UUID, ReviewAssessment> current = assessments.findByReviewIdIn(List.of(r.getId())).stream()
                .collect(Collectors.toMap(ReviewAssessment::getBenefitId, Function.identity()));
        Set<UUID> seen = new HashSet<>();
        for (AssessmentInput in : req.assessments() == null ? List.<AssessmentInput>of() : req.assessments()) {
            if (!byId.containsKey(in.benefitId())) {
                throw new BusinessRuleException("A benefit in the review is not one of this project's benefits");
            }
            if (!seen.add(in.benefitId())) {
                throw new BusinessRuleException("B" + byId.get(in.benefitId()).getNumber() + " is assessed twice");
            }
            ReviewAssessment a = current.computeIfAbsent(in.benefitId(), id -> new ReviewAssessment(r.getId(), id));
            a.assess(in.rating(), in.comment());
            assessments.save(a);
        }
        if (complete) {
            List<String> missing = list.stream().filter(b -> b.getStatus() != BenefitStatus.CANCELLED)
                    .filter(b -> !current.containsKey(b.getId())).map(b -> "B" + b.getNumber()).toList();
            if (!missing.isEmpty()) {
                throw new BusinessRuleException("Assess every benefit before completing the review: "
                        + String.join(", ", missing));
            }
            // Freeze each benefit's position as it stands today.
            Map<UUID, List<Measurement>> ms = measurementsOf(tenantId, list);
            for (ReviewAssessment a : current.values()) {
                Benefit b = byId.get(a.getBenefitId());
                Measurement last = lastOf(ms.get(b.getId()));
                a.freeze(b.getBaselineValue(), b.getTargetValue(), last == null ? null : last.getValue(), last == null ? null : last.getMeasuredOn(),
                        last == null ? null : BenefitRules.progressPct(b.getBaselineValue(), b.getTargetValue(),
                                last.getValue()));
            }
            r.complete(me.getId(), now);
            audit.record(tenantId, "benefit.review_completed", "Project", projectId,
                    JsonText.object("title", r.getTitle(), "conductedOn", r.getConductedOn(), "objectivesMet",
                            r.getObjectivesMet(), "benefits", current.size()));
            Set<UUID> tell = new HashSet<>(currentUser.withRole(tenantId, Roles.PMO_LEAD).stream().map(User::getId)
                    .toList());
            tell.remove(me.getId());
            notifications.notifyUsers(tenantId, tell, new Message("benefit.review_completed",
                    cap(p.getCode() + ": " + r.getTitle() + " completed", 200),
                    "Objectives met: " + r.getObjectivesMet().name().toLowerCase() + ". " + cap(r.getSummary(), 800),
                    link(p)));
        } else {
            audit.record(tenantId, "benefit.review_saved", "Project", projectId,
                    JsonText.object("title", r.getTitle(), "benefits", seen.size()));
        }
        return projectView(p, me);
    }

    @Transactional
    public ProjectBenefitsView cancelReview(UUID tenantId, UUID projectId, UUID reviewId, String note) {
        User me = requireEditor("cancel post-implementation reviews");
        Project p = projects.get(tenantId, projectId);
        Review r = review(tenantId, projectId, reviewId);
        r.cancel(note, now());
        audit.record(tenantId, "benefit.review_cancelled", "Project", projectId,
                JsonText.object("title", r.getTitle(), "note", note));
        return projectView(p, me);
    }

    // ================================================================== reminders

    /**
     * One tenant's reminders (its own transaction, so one failure does not lose the others): owners of
     * measurements that have fallen due, once per due date, and leads of reviews due within a week, once.
     * Skips inactive people and cancelled projects. Returns the notifications sent.
     */
    @Transactional
    public int sendReminders(UUID tenantId) {
        LocalDate today = today();
        int sent = 0;
        List<Benefit> active = benefits.findByTenantIdAndStatusIn(tenantId, List.of(BenefitStatus.ACTIVE));
        List<Review> planned = reviews.findByTenantIdAndStatusAndRemindedFalse(tenantId, ReviewStatus.PLANNED);
        Set<UUID> projectIds = new HashSet<>();
        Set<UUID> userIds = new HashSet<>();
        active.forEach(b -> {
            projectIds.add(b.getProjectId());
            userIds.add(b.getOwnerId());
        });
        planned.forEach(r -> {
            projectIds.add(r.getProjectId());
            userIds.add(r.getLeadId());
        });
        Map<UUID, Project> projectMap = projects.byIds(tenantId, projectIds);
        Set<UUID> activeUsers = currentUser.byIds(tenantId, userIds).stream().filter(User::isActive)
                .map(User::getId).collect(Collectors.toSet());
        Map<UUID, List<Measurement>> ms = measurementsOf(tenantId, active);
        for (Benefit b : active) {
            Project p = projectMap.get(b.getProjectId());
            if (p == null || p.getStatus() == ProjectStatus.CANCELLED || !activeUsers.contains(b.getOwnerId())) {
                continue;
            }
            Measurement last = lastOf(ms.get(b.getId()));
            LocalDate due = BenefitRules.nextDue(b.getFrequency(), b.getBaselineDate(),
                    last == null ? null : last.getMeasuredOn());
            if (due.isAfter(today) || due.equals(b.getRemindedDue())) {
                continue;
            }
            notifications.notifyUsers(tenantId, List.of(b.getOwnerId()), new Message("benefit.measurement_due",
                    cap(p.getCode() + " B" + b.getNumber() + ": measurement due", 200),
                    cap("Record " + b.getMeasure() + " (" + b.getUnit() + "), due " + due + ".", 1000), link(p)));
            b.reminded(due);
            sent++;
        }
        for (Review r : planned) {
            Project p = projectMap.get(r.getProjectId());
            if (r.getDueDate().isAfter(today.plusDays(REVIEW_REMINDER_DAYS)) || p == null
                    || p.getStatus() == ProjectStatus.CANCELLED || !activeUsers.contains(r.getLeadId())) {
                continue;
            }
            notifications.notifyUsers(tenantId, List.of(r.getLeadId()), new Message("benefit.review_due",
                    cap(p.getCode() + ": " + r.getTitle() + " due " + r.getDueDate(), 200),
                    "Assess each benefit against its target and record the findings.", link(p)));
            r.reminded();
            sent++;
        }
        return sent;
    }

    // ================================================================== views

    /** Per-request lookups shared by the views. */
    private record Ctx(Map<UUID, List<Measurement>> measurements, Map<UUID, List<ReviewAssessment>> assessments,
            Map<UUID, Benefit> benefits, Map<UUID, User> people, LocalDate today) {
    }

    private Ctx context(UUID tenantId, List<Benefit> list, List<Review> rs, User me) {
        Map<UUID, List<Measurement>> ms = measurementsOf(tenantId, list);
        Map<UUID, List<ReviewAssessment>> as = rs.isEmpty() ? Map.of()
                : assessments.findByReviewIdIn(rs.stream().map(Review::getId).toList()).stream()
                        .collect(Collectors.groupingBy(ReviewAssessment::getReviewId));
        Set<UUID> ids = new HashSet<>();
        list.forEach(b -> {
            ids.add(b.getOwnerId());
            ids.add(b.getClosedBy());
        });
        ms.values().forEach(l -> l.forEach(m -> ids.add(m.getRecordedBy())));
        rs.forEach(r -> {
            ids.add(r.getLeadId());
            ids.add(r.getCompletedBy());
        });
        return new Ctx(ms, as, list.stream().collect(Collectors.toMap(Benefit::getId, Function.identity())),
                people(tenantId, ids), today());
    }

    private ProjectBenefitsView projectView(Project p, User me) {
        UUID tenantId = p.getTenantId();
        List<Benefit> list = benefits.findByTenantIdAndProjectIdOrderByNumberAsc(tenantId, p.getId());
        List<Review> rs = reviews.findByTenantIdAndProjectIdOrderByDueDateAsc(tenantId, p.getId());
        Ctx ctx = context(tenantId, list, rs, me);
        List<BenefitView> views = list.stream().map(b -> benefitView(b, p, ctx, me)).toList();
        boolean editor = isEditor(me) && p.getStatus() != ProjectStatus.CANCELLED;
        return new ProjectBenefitsView(p.getId(), closeout.acceptedHandoverDate(tenantId, p.getId()).orElse(null),
                summary(views, rs.stream().filter(r -> r.getStatus() == ReviewStatus.PLANNED).toList()), views,
                rs.stream().map(r -> reviewView(r, p, ctx, me)).toList(), new Permissions(editor, editor));
    }

    private BenefitView benefitView(Benefit b, Project p, Ctx ctx, User me) {
        List<Measurement> ms = ctx.measurements().getOrDefault(b.getId(), List.of());
        Measurement last = lastOf(ms);
        BigDecimal progress = last == null ? null
                : BenefitRules.progressPct(b.getBaselineValue(), b.getTargetValue(), last.getValue());
        BigDecimal expected = BenefitRules.expectedPct(b.getBaselineDate(), b.getTargetDate(), ctx.today());
        LocalDate due = BenefitRules.nextDue(b.getFrequency(), b.getBaselineDate(),
                last == null ? null : last.getMeasuredOn());
        boolean editor = isEditor(me);
        boolean active = b.getStatus().isOpen();
        boolean owner = me.getId().equals(b.getOwnerId());
        List<MeasurementView> mv = ms.stream().map(m -> new MeasurementView(m.getId(), m.getMeasuredOn(), m.getValue(),
                BenefitRules.progressPct(b.getBaselineValue(), b.getTargetValue(), m.getValue()), m.getSource(),
                m.getNote(), ref(ctx.people(), m.getRecordedBy()),
                active && (editor || me.getId().equals(m.getRecordedBy())))).toList();
        return new BenefitView(b.getId(), projectRef(p, b.getProjectId()), b.getNumber(), b.getTitle(),
                b.getDescription(), b.getCategory(), b.getObjective(), ref(ctx.people(), b.getOwnerId()), b.getMeasure(),
                b.getUnit(), b.getDirection(), b.getBaselineValue(), b.getBaselineDate(), b.getTargetValue(),
                b.getTargetDate(), b.getFrequency(), b.getAnnualValueMinor(), b.getStatus(), b.getClosedNote(),
                ref(ctx.people(), b.getClosedBy()), b.getClosedAt(), last == null ? null : last.getValue(),
                last == null ? null : last.getMeasuredOn(), progress, expected,
                active ? BenefitRules.rating(progress, expected)
                        : b.getStatus() == BenefitStatus.ACHIEVED ? BenefitRating.ACHIEVED : null,
                active ? due : null, active && due.isBefore(ctx.today()),
                BenefitRules.realisedMinor(b.getAnnualValueMinor(), progress), mv, editor && active,
                active && (editor || owner), editor && active, me.hasRole(Roles.PMO_LEAD) && !active, b.getVersion());
    }

    private ReviewView reviewView(Review r, Project p, Ctx ctx, User me) {
        List<ReviewAssessment> as = ctx.assessments().getOrDefault(r.getId(), List.of());
        List<AssessmentView> av = as.stream()
                .filter(a -> ctx.benefits().containsKey(a.getBenefitId()))
                .map(a -> {
                    Benefit b = ctx.benefits().get(a.getBenefitId());
                    return new AssessmentView(b.getId(), b.getNumber(), b.getTitle(), b.getUnit(),
                            a.getBaselineValue() != null ? a.getBaselineValue() : b.getBaselineValue(),
                            a.getTargetValue() != null ? a.getTargetValue() : b.getTargetValue(),
                            a.getActualValue(), a.getMeasuredOn(), a.getProgressPct(), a.getRating(), a.getComment());
                })
                .sorted(Comparator.comparingInt(AssessmentView::benefitNumber)).toList();
        boolean planned = r.getStatus() == ReviewStatus.PLANNED;
        boolean editor = isEditor(me);
        boolean lead = me.getId().equals(r.getLeadId());
        return new ReviewView(r.getId(), projectRef(p, r.getProjectId()), r.getTitle(), r.getDueDate(),
                ref(ctx.people(), r.getLeadId()), r.getStatus(), r.getConductedOn(), r.getObjectivesMet(),
                r.getSummary(), r.getRecommendations(), r.getCancelNote(), ref(ctx.people(), r.getCompletedBy()),
                r.getCompletedAt(), planned && r.getDueDate().isBefore(ctx.today()), av, planned && editor,
                planned && (editor || lead), planned && editor, r.getVersion());
    }

    private static Summary summary(List<BenefitView> views, List<Review> plannedReviews) {
        int active = 0, achieved = 0, onTrack = 0, atRisk = 0, offTrack = 0, notMeasured = 0, overdue = 0;
        long value = 0, realised = 0;
        for (BenefitView v : views) {
            if (v.status() == BenefitStatus.CANCELLED) {
                continue;
            }
            if (v.annualValueMinor() != null) {
                value += v.annualValueMinor();
            }
            realised += v.realisedMinor();
            if (v.status() == BenefitStatus.ACHIEVED) {
                achieved++;
                continue;
            }
            if (v.status() != BenefitStatus.ACTIVE) {
                continue;
            }
            active++;
            overdue += v.overdue() ? 1 : 0;
            if (v.rating() == null) {
                notMeasured++;
            } else {
                switch (v.rating()) {
                    case ACHIEVED, ON_TRACK -> onTrack++;
                    case AT_RISK -> atRisk++;
                    case OFF_TRACK -> offTrack++;
                }
            }
        }
        return new Summary(views.size(), active, achieved, onTrack, atRisk, offTrack, notMeasured, overdue, value,
                realised, plannedReviews.size());
    }

    // ================================================================== helpers

    private Map<UUID, List<Measurement>> measurementsOf(UUID tenantId, Collection<Benefit> list) {
        if (list.isEmpty()) {
            return Map.of();
        }
        return measurements.findByTenantIdAndBenefitIdInOrderByMeasuredOnAsc(tenantId,
                list.stream().map(Benefit::getId).toList()).stream()
                .collect(Collectors.groupingBy(Measurement::getBenefitId));
    }

    private static Measurement lastOf(List<Measurement> ms) {
        return ms == null || ms.isEmpty() ? null
                : ms.stream().max(Comparator.comparing(Measurement::getMeasuredOn)).orElse(null);
    }

    private static Benefit.Definition definition(BenefitRequest r) {
        return new Benefit.Definition(r.title(), r.description(), r.category(), r.objective(), r.ownerId(),
                r.measure(), r.unit(), r.baselineValue(), r.baselineDate(), r.targetValue(), r.targetDate(),
                r.frequency(), r.annualValueMinor());
    }

    private static String benefitAudit(Benefit b) {
        return JsonText.object("benefit", "B" + b.getNumber(), "title", b.getTitle(), "measure", b.getMeasure(),
                "baseline", b.getBaselineValue(), "target", b.getTargetValue(), "targetDate", b.getTargetDate(),
                "owner", b.getOwnerId());
    }

    private void notifyOwner(UUID tenantId, Project p, Benefit b, User me) {
        if (!b.getOwnerId().equals(me.getId())) {
            notifications.notifyUsers(tenantId, List.of(b.getOwnerId()), new Message("benefit.owner",
                    cap(p.getCode() + " B" + b.getNumber() + ": you own this benefit", 200),
                    cap(b.getTitle() + ". Record " + b.getMeasure() + " " + b.getFrequency().name().toLowerCase()
                            .replace('_', '-') + " until " + b.getTargetDate() + ".", 1000), link(p)));
        }
    }

    private void notifyLead(UUID tenantId, Project p, Review r, User me) {
        if (!r.getLeadId().equals(me.getId())) {
            notifications.notifyUsers(tenantId, List.of(r.getLeadId()), new Message("benefit.review_planned",
                    cap(p.getCode() + ": you lead the " + r.getTitle(), 200), "Due " + r.getDueDate() + ".", link(p)));
        }
    }

    private void requireUsable(Project p) {
        if (p.getStatus() == ProjectStatus.CANCELLED) {
            throw new BusinessRuleException("Project " + p.getCode() + " is cancelled");
        }
    }

    private void checkUser(UUID tenantId, UUID userId, String what) {
        boolean ok = userId != null && currentUser.byIds(tenantId, Set.of(userId)).stream().anyMatch(User::isActive);
        if (!ok) {
            throw new BusinessRuleException(what + " must be an active user of this organisation");
        }
    }

    /** Serialises numbering of a project's benefits. */
    private void lockProject(UUID projectId) {
        jdbc.queryForList("SELECT pg_advisory_xact_lock(?)",
                (projectId.getMostSignificantBits() ^ projectId.getLeastSignificantBits()) * 31 + 0x42454E46L);
    }

    private Benefit lockedBenefit(UUID tenantId, UUID projectId, UUID id) {
        return benefits.lock(tenantId, id).filter(b -> b.getProjectId().equals(projectId))
                .orElseThrow(() -> new NotFoundException("Benefit not found"));
    }

    /** When the client says which version it edited, refuse if someone else saved since. */
    private static void checkVersion(Long sent, long current, String what) {
        if (sent != null && sent != current) {
            throw new BusinessRuleException(what + " was changed by someone else; reload and try again");
        }
    }

    private Review review(UUID tenantId, UUID projectId, UUID id) {
        return reviews.findByTenantIdAndId(tenantId, id).filter(r -> r.getProjectId().equals(projectId))
                .orElseThrow(() -> new NotFoundException("Review not found"));
    }

    private User requireEditor(String action) {
        User me = currentUser.require();
        Roles.requireAny(me, Roles.PLAN_EDITORS, action);
        return me;
    }

    private static boolean isEditor(User me) {
        return me.getRoles().stream().anyMatch(Roles.PLAN_EDITORS::contains);
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

    private static ProjectRef projectRef(Project p, UUID id) {
        return p == null ? new ProjectRef(id, "?", "Unknown project") : new ProjectRef(p.getId(), p.getCode(), p.getName());
    }

    private static String code(Map<UUID, Project> projectMap, UUID id) {
        Project p = projectMap.get(id);
        return p == null ? "" : p.getCode();
    }

    private static String cap(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private static String link(Project p) {
        return "/projects/" + p.getId() + "?tab=benefits";
    }

    private Instant now() {
        return Instant.now(clock);
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(LOCAL_ZONE));
    }
}
