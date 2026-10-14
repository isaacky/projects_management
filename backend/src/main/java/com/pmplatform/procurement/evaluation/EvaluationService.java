package com.pmplatform.procurement.evaluation;

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
import com.pmplatform.procurement.config.ApprovalStage;
import com.pmplatform.procurement.config.CriterionStage;
import com.pmplatform.procurement.config.EvaluationCommitteeRule;
import com.pmplatform.procurement.config.EvaluationCommitteeRuleRepository;
import com.pmplatform.procurement.config.EvaluationTemplate;
import com.pmplatform.procurement.config.EvaluationTemplateRepository;
import com.pmplatform.procurement.config.ProcurementMethod;
import com.pmplatform.procurement.config.ProcurementMethodRepository;
import com.pmplatform.procurement.config.ProcurementRules;
import com.pmplatform.procurement.config.ProcurementSettingsService;
import com.pmplatform.procurement.evaluation.EvaluationRules.Mark;
import com.pmplatform.procurement.evaluation.EvaluationRules.PreliminaryResult;
import com.pmplatform.procurement.evaluation.EvaluationRules.Step;
import com.pmplatform.procurement.evaluation.EvaluationRules.TechnicalResult;
import com.pmplatform.procurement.evaluation.api.EvaluationDtos.ApprovalView;
import com.pmplatform.procurement.evaluation.api.EvaluationDtos.Award;
import com.pmplatform.procurement.evaluation.api.EvaluationDtos.BidRow;
import com.pmplatform.procurement.evaluation.api.EvaluationDtos.CommitteeChangeRequest;
import com.pmplatform.procurement.evaluation.api.EvaluationDtos.CommitteeRequest;
import com.pmplatform.procurement.evaluation.api.EvaluationDtos.CriteriaRequest;
import com.pmplatform.procurement.evaluation.api.EvaluationDtos.CriterionRequest;
import com.pmplatform.procurement.evaluation.api.EvaluationDtos.CriterionView;
import com.pmplatform.procurement.evaluation.api.EvaluationDtos.Decision;
import com.pmplatform.procurement.evaluation.api.EvaluationDtos.DecisionRequest;
import com.pmplatform.procurement.evaluation.api.EvaluationDtos.EvaluationDetail;
import com.pmplatform.procurement.evaluation.api.EvaluationDtos.EvaluationResponse;
import com.pmplatform.procurement.evaluation.api.EvaluationDtos.MarkRequest;
import com.pmplatform.procurement.evaluation.api.EvaluationDtos.MarkView;
import com.pmplatform.procurement.evaluation.api.EvaluationDtos.MarksRequest;
import com.pmplatform.procurement.evaluation.api.EvaluationDtos.Me;
import com.pmplatform.procurement.evaluation.api.EvaluationDtos.MemberRequest;
import com.pmplatform.procurement.evaluation.api.EvaluationDtos.MemberView;
import com.pmplatform.procurement.evaluation.api.EvaluationDtos.Permissions;
import com.pmplatform.procurement.evaluation.api.EvaluationDtos.Recommendation;
import com.pmplatform.procurement.evaluation.api.EvaluationDtos.ReportRequest;
import com.pmplatform.procurement.evaluation.api.EvaluationDtos.StageProgress;
import com.pmplatform.procurement.evaluation.api.EvaluationDtos.TemplateOption;
import com.pmplatform.procurement.requisition.RequisitionRepository;
import com.pmplatform.procurement.supplier.Supplier;
import com.pmplatform.procurement.supplier.SupplierService;
import com.pmplatform.procurement.tender.Bid;
import com.pmplatform.procurement.tender.BidRepository;
import com.pmplatform.procurement.tender.BidStatus;
import com.pmplatform.procurement.tender.EvaluationStage;
import com.pmplatform.procurement.tender.Tender;
import com.pmplatform.procurement.tender.TenderRepository;
import com.pmplatform.procurement.tender.TenderStatus;
import com.pmplatform.procurement.tender.api.TenderDtos.Ref;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bid evaluation and award, under the Public Procurement and Asset Disposal Act pattern:
 *
 * <ol>
 *   <li>Procurement staff set up the evaluation: criteria (from a settings template) and the
 *       proposed committee. The accounting officer appoints the committee.</li>
 *   <li>Each member declares any conflict of interest; members with a conflict take no part.</li>
 *   <li>Preliminary (mandatory, pass/fail) and technical (scored) stages: each member marks
 *       every bid; the chair closes a stage once at least a quorum of members have finished.
 *       Bids that fail are out, and in a two-envelope tender their financial envelope stays sealed.</li>
 *   <li>Financial: after the financial opening the chair records each remaining bid's evaluated
 *       price (read-out price with arithmetic corrections); bids are ranked by price.</li>
 *   <li>The chair submits the recommendation, the signatories sign, the head of procurement gives
 *       a professional opinion and the award approval chain from the settings decides.</li>
 * </ol>
 *
 * Every step is in the tender's audit history.
 */
@Service
public class EvaluationService {

    static final EnumSet<EvaluationStatus> COMMITTEE_CHANGE_STAGES = EnumSet.of(EvaluationStatus.PRELIMINARY,
            EvaluationStatus.TECHNICAL, EvaluationStatus.FINANCIAL, EvaluationStatus.REPORT);

    private final EvaluationRepository evaluations;
    private final EvaluationMemberRepository members;
    private final EvaluationCriterionRepository criteria;
    private final EvaluationScoreRepository scores;
    private final EvaluationBidResultRepository results;
    private final EvaluationApprovalRepository approvals;
    private final TenderRepository tenders;
    private final BidRepository bids;
    private final EvaluationTemplateRepository templates;
    private final EvaluationCommitteeRuleRepository committeeRules;
    private final ProcurementMethodRepository methods;
    private final ProcurementSettingsService settings;
    private final RequisitionRepository requisitions;
    private final SupplierService suppliers;
    private final CurrentUserService currentUser;
    private final NotificationService notifications;
    private final AuditService audit;
    private final Clock clock;

    public EvaluationService(EvaluationRepository evaluations, EvaluationMemberRepository members,
            EvaluationCriterionRepository criteria, EvaluationScoreRepository scores,
            EvaluationBidResultRepository results, EvaluationApprovalRepository approvals, TenderRepository tenders,
            BidRepository bids, EvaluationTemplateRepository templates,
            EvaluationCommitteeRuleRepository committeeRules, ProcurementMethodRepository methods,
            ProcurementSettingsService settings, RequisitionRepository requisitions, SupplierService suppliers,
            CurrentUserService currentUser, NotificationService notifications, AuditService audit, Clock clock) {
        this.evaluations = evaluations;
        this.members = members;
        this.criteria = criteria;
        this.scores = scores;
        this.results = results;
        this.approvals = approvals;
        this.tenders = tenders;
        this.bids = bids;
        this.templates = templates;
        this.committeeRules = committeeRules;
        this.methods = methods;
        this.settings = settings;
        this.requisitions = requisitions;
        this.suppliers = suppliers;
        this.currentUser = currentUser;
        this.notifications = notifications;
        this.audit = audit;
        this.clock = clock;
    }

    // ================================================================== used by tenders

    /**
     * Who may read opened bid files: procurement staff, the accounting officer, and committee
     * members who have declared no conflict of interest.
     */
    @Transactional(readOnly = true)
    public boolean mayReadBids(UUID tenantId, UUID tenderId, User me) {
        if (isProcurement(me) || me.hasRole(Roles.ACCOUNTING_OFFICER)) {
            return true;
        }
        return evaluations.findByTenantIdAndTenderId(tenantId, tenderId)
                .flatMap(e -> member(members.findByEvaluationIdOrderByAddedAtAsc(e.getId()), me.getId())
                        .filter(m -> m.isCleared(e.isRequiresCoiDeclaration())))
                .isPresent();
    }

    /** Financial envelopes are opened only after the technical evaluation, for the bids that passed it. */
    @Transactional(readOnly = true)
    public Optional<String> financialOpeningBlocked(UUID tenantId, UUID tenderId) {
        Optional<Evaluation> e = evaluations.findByTenantIdAndTenderId(tenantId, tenderId);
        if (e.isEmpty() || e.get().getStatus() == EvaluationStatus.SETUP) {
            return Optional.of("Set up and appoint the evaluation committee, and complete the technical "
                    + "evaluation, before opening the financial envelopes");
        }
        if (!e.get().technicalDone()) {
            return Optional.of("The financial envelopes are opened after the technical evaluation is closed");
        }
        if (e.get().getStatus() != EvaluationStatus.FINANCIAL) {
            return Optional.of("No bid passed the technical evaluation, so there are no financial envelopes to open");
        }
        return Optional.empty();
    }

    @Transactional(readOnly = true)
    public Optional<EvaluationStatus> status(UUID tenantId, UUID tenderId) {
        return evaluations.findByTenantIdAndTenderId(tenantId, tenderId).map(Evaluation::getStatus);
    }

    // ================================================================== read

    @Transactional(readOnly = true)
    public EvaluationResponse get(UUID tenantId, UUID tenderId) {
        User me = currentUser.require();
        Tender t = loadTender(tenantId, tenderId);
        Optional<Evaluation> e = evaluations.findByTenantIdAndTenderId(tenantId, tenderId);
        if (e.isEmpty()) {
            String blocked = setUpBlockedReason(t);
            boolean can = isProcurement(me) && blocked == null;
            return new EvaluationResponse(false, can, isProcurement(me) ? blocked : "Procurement staff set up the evaluation",
                    can ? templateOptions(tenantId, t.getMethodCode()) : List.of(), null);
        }
        return response(t, e.get(), me);
    }

    // ================================================================== setup (procurement)

    @Transactional
    public EvaluationResponse setUp(UUID tenantId, UUID tenderId, String templateCode) {
        User me = requireProcurement("set up evaluations");
        Tender t = tenders.lockByTenantIdAndId(tenantId, tenderId)
                .orElseThrow(() -> new NotFoundException("Tender " + tenderId + " not found"));
        String blocked = setUpBlockedReason(t);
        if (blocked != null) {
            throw new BusinessRuleException(blocked);
        }
        if (evaluations.findByTenantIdAndTenderId(tenantId, tenderId).isPresent()) {
            throw new BusinessRuleException("This tender's evaluation is already set up");
        }
        EvaluationCommitteeRule rule = ProcurementRules
                .committeeRule(committeeRules.findByTenantId(tenantId), t.getMethodCode())
                .orElseThrow(() -> new BusinessRuleException("Set up an evaluation committee rule first "
                        + "(Procurement settings > Evaluation committees)"));
        EvaluationTemplate template = pickTemplate(tenantId, t.getMethodCode(), templateCode);
        Instant now = now();
        Evaluation e = evaluations.save(new Evaluation(tenantId, t.getId(), rule, template.getCode(), me.getId(), now));
        copyCriteria(e, template);
        audit.record(tenantId, "evaluation.set_up", "Tender", t.getId(),
                JsonText.object("number", t.getNumber(), "template", template.getCode(),
                        "quorum", e.getQuorum(), "passMarkPct", e.getTechnicalPassMarkPct()));
        return response(t, e, me);
    }

    /** Replaces the criteria with a template's (set-up stage only). */
    @Transactional
    public EvaluationResponse loadTemplate(UUID tenantId, UUID tenderId, String templateCode) {
        User me = requireProcurement("change evaluation criteria");
        Evaluation e = lock(tenantId, tenderId);
        Tender t = loadTender(tenantId, tenderId);
        EvaluationTemplate template = pickTemplate(tenantId, t.getMethodCode(), templateCode);
        e.useTemplate(template.getCode(), now());
        criteria.deleteAll(criteria.findByEvaluationIdOrderByStageAscSortOrderAsc(e.getId()));
        criteria.flush();
        copyCriteria(e, template);
        audit.record(tenantId, "evaluation.template_loaded", "Tender", t.getId(),
                JsonText.object("number", t.getNumber(), "template", template.getCode()));
        return response(t, e, me);
    }

    @Transactional
    public EvaluationResponse saveCriteria(UUID tenantId, UUID tenderId, CriteriaRequest req) {
        User me = requireProcurement("change evaluation criteria");
        Evaluation e = lock(tenantId, tenderId);
        Tender t = loadTender(tenantId, tenderId);
        Instant now = now();
        e.setPassMark(req.passMarkPct(), now);
        if (req.templateCode() != null) {
            e.useTemplate(pickTemplate(tenantId, t.getMethodCode(), req.templateCode()).getCode(), now);
        }
        List<EvaluationCriterion> next = new ArrayList<>();
        int order = 1;
        for (CriterionStage stage : CriterionStage.values()) {
            for (CriterionRequest c : req.criteria()) {
                if (c.stage() != stage) {
                    continue;
                }
                BigDecimal max = null;
                if (stage == CriterionStage.TECHNICAL) {
                    if (c.maxScore() == null || c.maxScore().signum() <= 0 || c.maxScore().scale() > 2) {
                        throw new BusinessRuleException("Technical criterion \"" + c.name()
                                + "\" needs a maximum score above 0 (at most 2 decimals)");
                    }
                    max = c.maxScore();
                }
                next.add(new EvaluationCriterion(e.getId(), stage, order++, c.name().trim(),
                        blank(c.description()), max));
            }
        }
        if (next.stream().noneMatch(c -> c.getStage() == CriterionStage.PRELIMINARY)) {
            throw new BusinessRuleException("Add at least one preliminary (pass/fail) criterion");
        }
        criteria.deleteAll(criteria.findByEvaluationIdOrderByStageAscSortOrderAsc(e.getId()));
        criteria.flush();
        criteria.saveAll(next);
        audit.record(tenantId, "evaluation.criteria_changed", "Tender", t.getId(),
                JsonText.object("number", t.getNumber(), "criteria", next.size(),
                        "technicalTotal", technicalTotal(next), "passMarkPct", e.getTechnicalPassMarkPct()));
        return response(t, e, me);
    }

    @Transactional
    public EvaluationResponse saveCommittee(UUID tenantId, UUID tenderId, CommitteeRequest req) {
        User me = requireProcurement("propose the evaluation committee");
        Evaluation e = lock(tenantId, tenderId);
        e.requireStatus(EvaluationStatus.SETUP, "change the proposed committee");
        Tender t = loadTender(tenantId, tenderId);
        Map<UUID, CommitteeRole> wanted = new LinkedHashMap<>();
        for (MemberRequest m : req.members()) {
            if (wanted.put(m.userId(), m.role()) != null) {
                throw new BusinessRuleException("Each person can be on the committee only once");
            }
        }
        Map<UUID, User> people = activeUsers(tenantId, wanted.keySet());
        Instant now = now();
        List<EvaluationMember> current = members.findByEvaluationIdOrderByAddedAtAsc(e.getId());
        for (EvaluationMember m : current) {
            if (!wanted.containsKey(m.getUserId())) {
                members.delete(m);
            } else {
                m.changeRole(wanted.remove(m.getUserId()));
            }
        }
        members.flush();
        wanted.forEach((userId, role) -> members.save(new EvaluationMember(e.getId(), userId, role, now)));
        e.changed(now);
        audit.record(tenantId, "evaluation.committee_proposed", "Tender", t.getId(),
                JsonText.object("number", t.getNumber(), "members", req.members().stream()
                        .map(m -> people.get(m.userId()).getDisplayName() + " (" + m.role() + ")")
                        .collect(Collectors.joining("; "))));
        return response(t, e, me);
    }

    /** The accounting officer appoints the committee; the evaluation starts. */
    @Transactional
    public EvaluationResponse appoint(UUID tenantId, UUID tenderId) {
        User me = currentUser.require();
        Roles.requireAny(me, Set.of(Roles.ACCOUNTING_OFFICER), "appoint evaluation committees");
        Evaluation e = lock(tenantId, tenderId);
        e.requireStatus(EvaluationStatus.SETUP, "be appointed");
        Tender t = loadTender(tenantId, tenderId);
        List<EvaluationMember> committee = members.findByEvaluationIdOrderByAddedAtAsc(e.getId());
        List<EvaluationCriterion> crit = criteria.findByEvaluationIdOrderByStageAscSortOrderAsc(e.getId());
        checkCommittee(e, committee, me);
        if (crit.stream().noneMatch(c -> c.getStage() == CriterionStage.PRELIMINARY)) {
            throw new BusinessRuleException("Add at least one preliminary (pass/fail) criterion first");
        }
        if (crit.stream().anyMatch(c -> c.getStage() == CriterionStage.TECHNICAL) && e.getTechnicalPassMarkPct() == null) {
            throw new BusinessRuleException("Set the technical pass mark first");
        }
        e.appoint(me.getId(), now());
        audit.record(tenantId, "evaluation.committee_appointed", "Tender", t.getId(),
                JsonText.object("number", t.getNumber(), "members", committeeText(tenantId, committee),
                        "criteria", crit.size(), "passMarkPct", e.getTechnicalPassMarkPct()));
        notifications.notifyUsers(tenantId, committee.stream().map(EvaluationMember::getUserId).toList(),
                new Message("evaluation.appointed", "Appointed to evaluate " + t.getNumber(),
                        "You are on the evaluation committee for \"" + t.getTitle() + "\". "
                                + (e.isRequiresCoiDeclaration() ? "Make your conflict of interest declaration first. " : "")
                                + "Then mark each bid against the criteria.", link(t)));
        return response(t, e, me);
    }

    /** After appointment the accounting officer may add a member or name a new chair, with a reason. */
    @Transactional
    public EvaluationResponse changeCommittee(UUID tenantId, UUID tenderId, CommitteeChangeRequest req) {
        User me = currentUser.require();
        Roles.requireAny(me, Set.of(Roles.ACCOUNTING_OFFICER), "change an appointed committee");
        Evaluation e = lock(tenantId, tenderId);
        if (!COMMITTEE_CHANGE_STAGES.contains(e.getStatus())) {
            throw new BusinessRuleException(e.getStatus() == EvaluationStatus.SETUP
                    ? "Procurement staff change the proposed committee until it is appointed"
                    : "The committee can no longer be changed once the report is submitted");
        }
        Tender t = loadTender(tenantId, tenderId);
        User person = activeUsers(tenantId, Set.of(req.userId())).get(req.userId());
        if (person.getId().equals(me.getId())) {
            throw new BusinessRuleException("The appointing officer cannot sit on the committee");
        }
        List<EvaluationMember> committee = members.findByEvaluationIdOrderByAddedAtAsc(e.getId());
        Optional<EvaluationMember> existing = member(committee, req.userId());
        Instant now = now();
        if (existing.isPresent()) {
            if (req.role() != CommitteeRole.CHAIR || existing.get().getRole() == CommitteeRole.CHAIR) {
                throw new BusinessRuleException(person.getDisplayName()
                        + " is already on the committee; only naming a new chair is supported");
            }
            if (existing.get().getRole() == CommitteeRole.SECRETARY) {
                throw new BusinessRuleException("The secretary does not vote and cannot become chair");
            }
        }
        if (req.role() == CommitteeRole.SECRETARY
                && committee.stream().anyMatch(m -> m.getRole() == CommitteeRole.SECRETARY)) {
            throw new BusinessRuleException("The committee already has a secretary");
        }
        if (existing.isEmpty() && req.role().isVoting()
                && committee.stream().filter(m -> m.getRole().isVoting()).count() >= e.getMaxMembers()) {
            throw new BusinessRuleException("The committee already has the maximum of " + e.getMaxMembers() + " members");
        }
        if (req.role() == CommitteeRole.CHAIR) {
            committee.stream().filter(m -> m.getRole() == CommitteeRole.CHAIR)
                    .forEach(m -> m.changeRole(CommitteeRole.MEMBER));
        }
        if (existing.isPresent()) {
            existing.get().changeRole(CommitteeRole.CHAIR);
        } else {
            members.save(new EvaluationMember(e.getId(), person.getId(), req.role(), now));
            notifications.notifyUsers(tenantId, List.of(person.getId()),
                    new Message("evaluation.appointed", "Appointed to evaluate " + t.getNumber(),
                            "You were added to the evaluation committee for \"" + t.getTitle() + "\".", link(t)));
        }
        e.changed(now);
        audit.record(tenantId, "evaluation.committee_changed", "Tender", t.getId(),
                JsonText.object("number", t.getNumber(), "person", person.getDisplayName(), "role", req.role(),
                        "reason", req.reason()));
        return response(t, e, me);
    }

    // ================================================================== members

    @Transactional
    public EvaluationResponse declare(UUID tenantId, UUID tenderId, boolean conflict, String text) {
        User me = currentUser.require();
        Evaluation e = lock(tenantId, tenderId);
        if (e.getStatus() == EvaluationStatus.SETUP || e.getStatus().isFinal()) {
            throw new BusinessRuleException("Declarations are made once the committee is appointed");
        }
        Tender t = loadTender(tenantId, tenderId);
        EvaluationMember m = member(members.findByEvaluationIdOrderByAddedAtAsc(e.getId()), me.getId())
                .orElseThrow(() -> new ForbiddenActionException("Only committee members make declarations"));
        m.declare(conflict, text, now());
        audit.record(tenantId, "evaluation.declaration", "Tender", t.getId(),
                JsonText.object("number", t.getNumber(), "member", me.getDisplayName(), "conflict", conflict,
                        "declaration", blank(text)));
        if (conflict) {
            notifications.notifyRole(tenantId, Roles.ACCOUNTING_OFFICER, new Message("evaluation.conflict_declared",
                    me.getDisplayName() + " declared a conflict on " + t.getNumber(),
                    "They take no further part. Check that the committee can still reach its quorum of "
                            + e.getQuorum() + ".", link(t)));
        }
        return response(t, e, me);
    }

    @Transactional
    public EvaluationResponse saveMarks(UUID tenantId, UUID tenderId, MarksRequest req) {
        User me = currentUser.require();
        Evaluation e = lock(tenantId, tenderId);
        if (!e.getStatus().isScoring()) {
            throw new BusinessRuleException("Marks are entered during the preliminary and technical stages");
        }
        Tender t = loadTender(tenantId, tenderId);
        EvaluationMember m = member(members.findByEvaluationIdOrderByAddedAtAsc(e.getId()), me.getId())
                .orElseThrow(() -> new ForbiddenActionException("Only committee members enter marks"));
        if (!m.mayScore(e.isRequiresCoiDeclaration())) {
            throw new ForbiddenActionException(m.isConflicted() ? "You declared a conflict of interest and cannot mark bids"
                    : !m.getRole().isVoting() ? "The secretary does not mark bids"
                    : "Make your conflict of interest declaration before marking bids");
        }
        CriterionStage stage = criterionStage(e.getStatus());
        Bid bid = bids.findByTenantIdAndTenderIdAndId(tenantId, t.getId(), req.bidId())
                .orElseThrow(() -> new NotFoundException("Bid " + req.bidId() + " not found"));
        if (!inStage(e.getStatus(), bid)) {
            throw new BusinessRuleException("Bid " + bid.getReceiptNo() + " is not being evaluated at this stage");
        }
        Map<UUID, EvaluationCriterion> crit = criteria.findByEvaluationIdOrderByStageAscSortOrderAsc(e.getId()).stream()
                .filter(c -> c.getStage() == stage)
                .collect(Collectors.toMap(EvaluationCriterion::getId, Function.identity()));
        Map<UUID, EvaluationScore> mine = scores.findByEvaluationId(e.getId()).stream()
                .filter(s -> s.getMemberId().equals(me.getId()) && s.getBidId().equals(bid.getId()))
                .collect(Collectors.toMap(EvaluationScore::getCriterionId, Function.identity()));
        Instant now = now();
        for (MarkRequest mark : req.marks()) {
            EvaluationCriterion c = crit.get(mark.criterionId());
            if (c == null) {
                throw new BusinessRuleException("That criterion is not part of the " + stage.name().toLowerCase()
                        + " stage");
            }
            Boolean passed = null;
            BigDecimal score = null;
            if (stage == CriterionStage.PRELIMINARY) {
                if (mark.passed() == null) {
                    throw new BusinessRuleException("Mark \"" + c.getName() + "\" as met or not met");
                }
                if (!mark.passed() && blank(mark.comment()) == null) {
                    throw new BusinessRuleException("Say why \"" + c.getName() + "\" is not met");
                }
                passed = mark.passed();
            } else {
                if (mark.score() == null) {
                    throw new BusinessRuleException("Give a score for \"" + c.getName() + "\"");
                }
                if (mark.score().signum() < 0 || mark.score().compareTo(c.getMaxScore()) > 0 || mark.score().scale() > 2) {
                    throw new BusinessRuleException("The score for \"" + c.getName() + "\" must be between 0 and "
                            + c.getMaxScore().stripTrailingZeros().toPlainString() + " (at most 2 decimals)");
                }
                score = mark.score();
            }
            EvaluationScore s = mine.get(c.getId());
            if (s == null) {
                s = new EvaluationScore(e.getId(), c.getId(), bid.getId(), me.getId());
            }
            s.set(passed, score, mark.comment(), now);
            scores.save(s);
        }
        // Individual marks stay private until the stage closes; the log records that they changed.
        audit.record(tenantId, "evaluation.marks_saved", "Tender", t.getId(),
                JsonText.object("number", t.getNumber(), "stage", e.getStatus(), "member", me.getDisplayName(),
                        "receiptNo", bid.getReceiptNo(), "marks", req.marks().size()));
        return response(t, e, me);
    }

    // ================================================================== chair

    @Transactional
    public EvaluationResponse closeStage(UUID tenantId, UUID tenderId) {
        User me = currentUser.require();
        Evaluation e = lock(tenantId, tenderId);
        Tender t = loadTender(tenantId, tenderId);
        List<EvaluationMember> committee = members.findByEvaluationIdOrderByAddedAtAsc(e.getId());
        requireChair(e, committee, me, "close a stage");
        Instant now = now();
        switch (e.getStatus()) {
            case PRELIMINARY -> closePreliminary(e, t, committee, now);
            case TECHNICAL -> closeTechnical(e, t, committee, now);
            case FINANCIAL -> closeFinancial(e, t, now);
            default -> throw new BusinessRuleException("There is no open evaluation stage to close");
        }
        notifications.notifyUsers(tenantId, committee.stream().map(EvaluationMember::getUserId)
                        .filter(id -> !id.equals(me.getId())).toList(),
                new Message("evaluation.stage_closed", t.getNumber() + ": evaluation moved to " + label(e.getStatus()),
                        "The chair closed the previous stage. The results are on the evaluation page.", link(t)));
        return response(t, e, me);
    }

    private void closePreliminary(Evaluation e, Tender t, List<EvaluationMember> committee, Instant now) {
        List<Bid> stageBids = stageBids(e.getStatus(), allBids(t));
        List<EvaluationCriterion> crit = stageCriteria(e, CriterionStage.PRELIMINARY);
        Set<UUID> complete = requireQuorum(e, committee, crit, stageBids);
        List<Mark> marks = marks(e);
        UUID chair = chairId(committee);
        Map<UUID, String> names = crit.stream().collect(Collectors.toMap(EvaluationCriterion::getId,
                EvaluationCriterion::getName));
        Map<UUID, EvaluationBidResult> res = resultsByBid(e);
        int passed = 0;
        List<String> outcome = new ArrayList<>();
        for (Bid b : stageBids) {
            PreliminaryResult r = EvaluationRules.preliminary(crit.stream().map(EvaluationCriterion::getId).toList(),
                    b.getId(), complete, chair, marks);
            String note = r.passed() ? null : cap("Did not meet: " + r.failedCriteria().stream().map(names::get)
                    .collect(Collectors.joining("; ")), 2000);
            EvaluationBidResult row = res.computeIfAbsent(b.getId(), id -> new EvaluationBidResult(e.getId(), id, now));
            row.preliminary(r.passed(), note, now);
            results.save(row);
            if (r.passed()) {
                passed++;
            } else {
                b.disqualify(EvaluationStage.PRELIMINARY, note, now);
            }
            outcome.add(b.getReceiptNo() + (r.passed() ? " passed" : " failed"));
        }
        boolean technical = crit(e).stream().anyMatch(c -> c.getStage() == CriterionStage.TECHNICAL);
        e.closePreliminary(technical, passed > 0, now);
        audit.record(e.getTenantId(), "evaluation.preliminary_closed", "Tender", t.getId(),
                JsonText.object("number", t.getNumber(), "membersCounted", complete.size(),
                        "results", String.join("; ", outcome)));
    }

    private void closeTechnical(Evaluation e, Tender t, List<EvaluationMember> committee, Instant now) {
        List<Bid> stageBids = stageBids(e.getStatus(), allBids(t));
        List<EvaluationCriterion> crit = stageCriteria(e, CriterionStage.TECHNICAL);
        Set<UUID> complete = requireQuorum(e, committee, crit, stageBids);
        List<Mark> marks = marks(e);
        Map<UUID, BigDecimal> max = new LinkedHashMap<>();
        crit.forEach(c -> max.put(c.getId(), c.getMaxScore()));
        int passMark = e.getTechnicalPassMarkPct() == null ? 0 : e.getTechnicalPassMarkPct();
        Map<UUID, EvaluationBidResult> res = resultsByBid(e);
        int passed = 0;
        List<String> outcome = new ArrayList<>();
        for (Bid b : stageBids) {
            TechnicalResult r = EvaluationRules.technical(max, b.getId(), complete, marks, passMark);
            EvaluationBidResult row = res.computeIfAbsent(b.getId(), id -> new EvaluationBidResult(e.getId(), id, now));
            row.technical(r.total(), r.pct(), r.passed(), now);
            results.save(row);
            if (r.passed()) {
                passed++;
            } else {
                b.disqualify(EvaluationStage.TECHNICAL, "Technical score " + r.pct().toPlainString()
                        + "% is below the pass mark of " + passMark + "%", now);
            }
            outcome.add(b.getReceiptNo() + " " + r.pct().toPlainString() + "%");
        }
        e.closeTechnical(passed > 0, now);
        audit.record(e.getTenantId(), "evaluation.technical_closed", "Tender", t.getId(),
                JsonText.object("number", t.getNumber(), "membersCounted", complete.size(), "passMarkPct", passMark,
                        "results", String.join("; ", outcome)));
        if (passed > 0 && t.isTwoEnvelope()) {
            notifications.notifyRole(e.getTenantId(), Roles.PROCUREMENT_OFFICER, new Message("evaluation.ready_for_financial",
                    t.getNumber() + ": open the financial envelopes",
                    passed + " bid(s) passed the technical evaluation. Their financial envelopes can now be opened.",
                    link(t)));
        }
    }

    private void closeFinancial(Evaluation e, Tender t, Instant now) {
        if (!t.pricesOpen()) {
            throw new BusinessRuleException("Open the financial envelopes first");
        }
        List<Bid> stageBids = stageBids(e.getStatus(), allBids(t));
        Map<UUID, EvaluationBidResult> res = resultsByBid(e);
        Map<UUID, Long> prices = new HashMap<>();
        List<String> missing = new ArrayList<>();
        for (Bid b : stageBids) {
            EvaluationBidResult r = res.get(b.getId());
            if (r == null || r.getEvaluatedPriceMinor() == null) {
                missing.add(b.getReceiptNo());
            } else {
                prices.put(b.getId(), r.getEvaluatedPriceMinor());
            }
        }
        if (!missing.isEmpty()) {
            throw new BusinessRuleException("Record the evaluated price (or reject the bid) for: " + String.join(", ", missing));
        }
        Map<UUID, Integer> ranks = EvaluationRules.rank(prices);
        ranks.forEach((bidId, rank) -> res.get(bidId).rank(rank, now));
        e.closeFinancial(now);
        audit.record(e.getTenantId(), "evaluation.financial_closed", "Tender", t.getId(),
                JsonText.object("number", t.getNumber(), "ranking", stageBids.stream()
                        .sorted(Comparator.comparing(b -> ranks.get(b.getId())))
                        .map(b -> ranks.get(b.getId()) + ". " + b.getReceiptNo() + " " + prices.get(b.getId()))
                        .collect(Collectors.joining("; "))));
    }

    @Transactional
    public EvaluationResponse saveFinancial(UUID tenantId, UUID tenderId, UUID bidId, long priceMinor, String note) {
        User me = currentUser.require();
        Evaluation e = lock(tenantId, tenderId);
        e.requireStatus(EvaluationStatus.FINANCIAL, "record evaluated prices");
        Tender t = loadTender(tenantId, tenderId);
        requireChair(e, members.findByEvaluationIdOrderByAddedAtAsc(e.getId()), me, "record evaluated prices");
        if (!t.pricesOpen()) {
            throw new BusinessRuleException("Open the financial envelopes first");
        }
        Bid bid = bids.findByTenantIdAndTenderIdAndId(tenantId, t.getId(), bidId)
                .orElseThrow(() -> new NotFoundException("Bid " + bidId + " not found"));
        if (!bid.isInContention()) {
            throw new BusinessRuleException("Bid " + bid.getReceiptNo() + " is no longer being evaluated");
        }
        if (bid.getReadOutPriceMinor() != null && bid.getReadOutPriceMinor() != priceMinor && blank(note) == null) {
            throw new BusinessRuleException("The evaluated price differs from the price read out at opening; "
                    + "record the correction (for example the arithmetic error found)");
        }
        Instant now = now();
        EvaluationBidResult row = resultsByBid(e).computeIfAbsent(bid.getId(),
                id -> new EvaluationBidResult(e.getId(), id, now));
        Long before = row.getEvaluatedPriceMinor();
        row.price(priceMinor, note, now);
        results.save(row);
        audit.record(tenantId, "evaluation.price_evaluated", "Tender", t.getId(),
                JsonText.object("number", t.getNumber(), "receiptNo", bid.getReceiptNo(),
                        "readOutPriceMinor", bid.getReadOutPriceMinor(), "evaluatedPriceMinor", priceMinor,
                        "previousMinor", before, "note", blank(note)));
        return response(t, e, me);
    }

    /** Rejects a bid at the financial stage, e.g. a bidder who does not accept an arithmetic correction. */
    @Transactional
    public EvaluationResponse rejectAtFinancial(UUID tenantId, UUID tenderId, UUID bidId, String reason) {
        User me = currentUser.require();
        Evaluation e = lock(tenantId, tenderId);
        e.requireStatus(EvaluationStatus.FINANCIAL, "reject bids at the financial stage");
        Tender t = loadTender(tenantId, tenderId);
        requireChair(e, members.findByEvaluationIdOrderByAddedAtAsc(e.getId()), me, "reject bids");
        Bid bid = bids.findByTenantIdAndTenderIdAndId(tenantId, t.getId(), bidId)
                .orElseThrow(() -> new NotFoundException("Bid " + bidId + " not found"));
        bid.disqualify(EvaluationStage.FINANCIAL, reason, now());
        audit.record(tenantId, "evaluation.bid_rejected", "Tender", t.getId(),
                JsonText.object("number", t.getNumber(), "receiptNo", bid.getReceiptNo(), "stage", "FINANCIAL",
                        "reason", reason));
        return response(t, e, me);
    }

    @Transactional
    public EvaluationResponse submitReport(UUID tenantId, UUID tenderId, ReportRequest req) {
        User me = currentUser.require();
        Evaluation e = lock(tenantId, tenderId);
        e.requireStatus(EvaluationStatus.REPORT, "take a report");
        Tender t = loadTender(tenantId, tenderId);
        List<EvaluationMember> committee = members.findByEvaluationIdOrderByAddedAtAsc(e.getId());
        requireChair(e, committee, me, "submit the evaluation report");
        Map<UUID, EvaluationBidResult> res = resultsByBid(e);
        List<Bid> responsive = allBids(t).stream().filter(Bid::isInContention)
                .filter(b -> res.containsKey(b.getId()) && res.get(b.getId()).getFinancialRank() != null).toList();
        String text = req.recommendation().trim();
        boolean noAward = Boolean.TRUE.equals(req.noAward());
        Long amount = null;
        UUID bidId = null;
        if (noAward) {
            if (req.bidId() != null) {
                throw new BusinessRuleException("Choose either a bid to award or no award");
            }
        } else {
            if (responsive.isEmpty()) {
                throw new BusinessRuleException("No bid is responsive; the committee can only recommend no award");
            }
            Bid chosen = responsive.stream().filter(b -> b.getId().equals(req.bidId())).findFirst()
                    .orElseThrow(() -> new BusinessRuleException("Choose one of the responsive bids"));
            EvaluationBidResult r = res.get(chosen.getId());
            if (r.getFinancialRank() != 1) {
                if (blank(req.deviationReason()) == null) {
                    throw new BusinessRuleException("The chosen bid is not the lowest evaluated bid; "
                            + "give the reason for recommending it");
                }
                text = text + "\n\nReason for not recommending the lowest evaluated bid: " + req.deviationReason().trim();
            }
            bidId = chosen.getId();
            amount = r.getEvaluatedPriceMinor();
        }
        e.submitReport(me.getId(), bidId, amount, noAward, text, now());
        Set<UUID> signers = EvaluationRules.signatories(e.getReportSignatories(), committee, e.isRequiresCoiDeclaration());
        audit.record(tenantId, "evaluation.report_submitted", "Tender", t.getId(),
                JsonText.object("number", t.getNumber(), "round", e.getRound(), "noAward", noAward,
                        "recommendedReceiptNo", bidId == null ? null : receiptNo(responsive, bidId),
                        "amountMinor", amount));
        notifications.notifyUsers(tenantId, signers.stream().filter(id -> !id.equals(me.getId())).toList(),
                new Message("evaluation.sign", "Sign the evaluation report for " + t.getNumber(),
                        "The chair submitted the committee's recommendation. Review and sign it.", link(t)));
        return response(t, e, me);
    }

    // ================================================================== signatures and approval

    @Transactional
    public EvaluationResponse sign(UUID tenantId, UUID tenderId, String note) {
        User me = currentUser.require();
        Evaluation e = lock(tenantId, tenderId);
        e.requireStatus(EvaluationStatus.SIGNING, "be signed");
        Tender t = loadTender(tenantId, tenderId);
        List<EvaluationMember> committee = members.findByEvaluationIdOrderByAddedAtAsc(e.getId());
        Set<UUID> signers = EvaluationRules.signatories(e.getReportSignatories(), committee, e.isRequiresCoiDeclaration());
        if (!signers.contains(me.getId())) {
            throw new ForbiddenActionException("You are not a signatory of this report");
        }
        Instant now = now();
        member(committee, me.getId()).orElseThrow().sign(e.getRound(), note, now);
        audit.record(tenantId, "evaluation.report_signed", "Tender", t.getId(),
                JsonText.object("number", t.getNumber(), "round", e.getRound(), "member", me.getDisplayName(),
                        "note", blank(note)));
        boolean allSigned = committee.stream().filter(m -> signers.contains(m.getUserId()))
                .allMatch(m -> m.signedFor(e.getRound()));
        if (allSigned) {
            e.signed(now);
            long amount = e.isNoAward() ? t.getEstimatedValueMinor() : e.getRecommendedAmountMinor();
            List<Step> chain = EvaluationRules.approvalChain(
                    settings.check(tenantId, ApprovalStage.AWARD, t.getMethodCode(), amount, t.getCurrency()).approvers(),
                    e.getReportReviewerRole(), e.getAwardApproverRole());
            for (Step s : chain) {
                approvals.save(new EvaluationApproval(e.getId(), e.getRound(), s.order(), s.role(), s.opinion()));
            }
            audit.record(tenantId, "evaluation.sent_for_approval", "Tender", t.getId(),
                    JsonText.object("number", t.getNumber(), "round", e.getRound(), "steps", chain.stream()
                            .map(s -> s.order() + ". " + s.role() + (s.opinion() ? " (professional opinion)" : ""))
                            .collect(Collectors.joining("; "))));
            notifyStep(tenantId, t, chain.getFirst().role(), chain.getFirst().opinion());
        }
        return response(t, e, me);
    }

    @Transactional
    public EvaluationResponse decide(UUID tenantId, UUID tenderId, DecisionRequest req) {
        User me = currentUser.require();
        Evaluation e = lock(tenantId, tenderId);
        e.requireStatus(EvaluationStatus.APPROVAL, "take a decision");
        Tender t = tenders.lockByTenantIdAndId(tenantId, tenderId)
                .orElseThrow(() -> new NotFoundException("Tender " + tenderId + " not found"));
        List<EvaluationApproval> round = currentRound(e);
        EvaluationApproval step = round.stream().filter(a -> a.getStatus() == ApprovalStatus.PENDING).findFirst()
                .orElseThrow(() -> new BusinessRuleException("No approval step is waiting"));
        if (!me.hasRole(step.getApproverRole())) {
            throw new ForbiddenActionException("This step needs the " + step.getApproverRole() + " role");
        }
        List<EvaluationMember> committee = members.findByEvaluationIdOrderByAddedAtAsc(e.getId());
        if (member(committee, me.getId()).isPresent()) {
            throw new ForbiddenActionException("Committee members cannot approve their own recommendation");
        }
        boolean last = round.getLast().getId().equals(step.getId());
        Instant now = now();
        switch (req.decision()) {
            case APPROVE -> {
                step.decide(ApprovalStatus.APPROVED, me.getId(), req.note(), now);
                if (!last) {
                    EvaluationApproval next = round.get(round.indexOf(step) + 1);
                    notifyStep(tenantId, t, next.getApproverRole(), next.isOpinion());
                } else if (e.isNoAward()) {
                    e.terminate(now);
                    t.terminate(cap("No award: " + e.getRecommendation(), 2000), now);
                } else {
                    e.award(now);
                    int standstill = methods.findByTenantIdAndCode(tenantId, t.getMethodCode())
                            .map(ProcurementMethod::getStandstillDays).orElse(14);
                    t.award(e.getRecommendedBidId(), e.getRecommendedAmountMinor(), standstill, now);
                }
            }
            case RETURN -> {
                step.decide(ApprovalStatus.RETURNED, me.getId(), req.note(), now);
                round.forEach(EvaluationApproval::skip);
                e.returnToCommittee(now);
                notifications.notifyUsers(tenantId, committee.stream().filter(m -> m.getRole() == CommitteeRole.CHAIR)
                                .map(EvaluationMember::getUserId).toList(),
                        new Message("evaluation.returned", t.getNumber() + ": report returned to the committee",
                                me.getDisplayName() + ": " + req.note(), link(t)));
            }
            case TERMINATE -> {
                if (!last) {
                    throw new BusinessRuleException("Only the final approver can terminate the tender");
                }
                step.decide(ApprovalStatus.TERMINATED, me.getId(), req.note(), now);
                e.terminate(now);
                t.terminate(req.note(), now);
            }
            default -> throw new IllegalStateException();
        }
        audit.record(tenantId, "evaluation.decision", "Tender", t.getId(),
                JsonText.object("number", t.getNumber(), "round", e.getRound(), "step", step.getStepOrder(),
                        "role", step.getApproverRole(), "opinion", step.isOpinion(), "decision", req.decision(),
                        "by", me.getDisplayName(), "note", blank(req.note())));
        if (e.getStatus().isFinal()) {
            audit.record(tenantId, e.getStatus() == EvaluationStatus.AWARDED ? "tender.awarded" : "tender.terminated",
                    "Tender", t.getId(), JsonText.object("number", t.getNumber(),
                            "bidId", t.getAwardedBidId(), "amountMinor", t.getAwardedAmountMinor(),
                            "standstillEndsAt", t.getStandstillEndsAt(), "reason", t.getCancelledReason()));
            Set<UUID> told = new LinkedHashSet<>(committee.stream().map(EvaluationMember::getUserId).toList());
            requisitions.findById(t.getRequisitionId()).ifPresent(r -> told.add(r.getRequestedBy()));
            Message m = e.getStatus() == EvaluationStatus.AWARDED
                    ? new Message("tender.awarded", t.getNumber() + " awarded",
                            "The award was approved. Notify the bidders; the contract may be signed after "
                                    + t.getStandstillEndsAt() + ".", link(t))
                    : new Message("tender.terminated", t.getNumber() + " terminated", t.getCancelledReason(), link(t));
            notifications.notifyUsers(tenantId, told, m);
            notifications.notifyRole(tenantId, Roles.PROCUREMENT_OFFICER, m);
        }
        return response(t, e, me);
    }

    // ================================================================== helpers: loading and checks

    private String setUpBlockedReason(Tender t) {
        if (t.getStatus() == TenderStatus.CANCELLED || t.getStatus() == TenderStatus.TERMINATED
                || t.getStatus() == TenderStatus.AWARDED) {
            return "The tender is " + t.getStatus().name().toLowerCase();
        }
        if (!t.anyOpened()) {
            return "The evaluation is set up after the bids are opened";
        }
        return null;
    }

    private List<TemplateOption> templateOptions(UUID tenantId, String methodCode) {
        List<EvaluationTemplate> active = templates.findByTenantIdOrderByCodeAsc(tenantId).stream()
                .filter(EvaluationTemplate::isActive).toList();
        String suggested = suggestedTemplate(active, methodCode).map(EvaluationTemplate::getCode).orElse(null);
        return active.stream()
                .filter(x -> x.getMethodCode() == null || x.getMethodCode().equals(methodCode))
                .map(x -> new TemplateOption(x.getCode(), x.getName(), x.getMethodCode(), x.getCode().equals(suggested)))
                .toList();
    }

    private static Optional<EvaluationTemplate> suggestedTemplate(List<EvaluationTemplate> active, String methodCode) {
        return active.stream().filter(x -> methodCode.equals(x.getMethodCode())).findFirst()
                .or(() -> active.stream().filter(x -> x.getMethodCode() == null).findFirst());
    }

    private EvaluationTemplate pickTemplate(UUID tenantId, String methodCode, String code) {
        List<EvaluationTemplate> active = templates.findByTenantIdOrderByCodeAsc(tenantId).stream()
                .filter(EvaluationTemplate::isActive).toList();
        if (code == null) {
            return suggestedTemplate(active, methodCode).orElseThrow(() -> new BusinessRuleException(
                    "Add an evaluation criteria template first (Procurement settings > Evaluation criteria)"));
        }
        EvaluationTemplate x = active.stream().filter(a -> a.getCode().equals(code)).findFirst()
                .orElseThrow(() -> new BusinessRuleException("No active evaluation template " + code));
        if (x.getMethodCode() != null && !x.getMethodCode().equals(methodCode)) {
            throw new BusinessRuleException("Template " + code + " is for " + x.getMethodCode() + " tenders");
        }
        return x;
    }

    private void copyCriteria(Evaluation e, EvaluationTemplate template) {
        criteria.saveAll(template.getCriteria().stream().map(c -> new EvaluationCriterion(e.getId(), c.getStage(),
                c.getSortOrder(), c.getName(), c.getDescription(), c.getMaxScore())).toList());
    }

    private void checkCommittee(Evaluation e, List<EvaluationMember> committee, User appointer) {
        long voting = committee.stream().filter(m -> m.getRole().isVoting()).count();
        long chairs = committee.stream().filter(m -> m.getRole() == CommitteeRole.CHAIR).count();
        long secretaries = committee.stream().filter(m -> m.getRole() == CommitteeRole.SECRETARY).count();
        if (chairs != 1) {
            throw new BusinessRuleException("The committee needs exactly one chair");
        }
        if (secretaries > 1) {
            throw new BusinessRuleException("The committee can have at most one secretary");
        }
        if (voting < e.getMinMembers() || voting > e.getMaxMembers()) {
            throw new BusinessRuleException("The committee needs between " + e.getMinMembers() + " and "
                    + e.getMaxMembers() + " members (chair included, secretary not counted); it has " + voting);
        }
        if (member(committee, appointer.getId()).isPresent()) {
            throw new BusinessRuleException("The appointing officer cannot sit on the committee");
        }
    }

    private Set<UUID> requireQuorum(Evaluation e, List<EvaluationMember> committee, List<EvaluationCriterion> crit,
            List<Bid> stageBids) {
        List<EvaluationMember> scoring = committee.stream().filter(m -> m.mayScore(e.isRequiresCoiDeclaration())).toList();
        Set<UUID> complete = EvaluationRules.completeMembers(scoring.stream().map(EvaluationMember::getUserId).toList(),
                crit.stream().map(EvaluationCriterion::getId).toList(), stageBids.stream().map(Bid::getId).toList(),
                marks(e));
        if (stageBids.isEmpty()) {
            return complete;
        }
        if (complete.size() < e.getQuorum()) {
            Map<UUID, User> people = people(e.getTenantId(), committee.stream().map(EvaluationMember::getUserId).toList());
            String waiting = committee.stream().filter(m -> m.getRole().isVoting() && !m.isConflicted())
                    .filter(m -> !complete.contains(m.getUserId()))
                    .map(m -> name(people, m.getUserId())).collect(Collectors.joining(", "));
            throw new BusinessRuleException(complete.size() + " member(s) have finished marking; the quorum is "
                    + e.getQuorum() + ". Waiting for: " + (waiting.isEmpty() ? "nobody available - "
                    + "the accounting officer must appoint more members" : waiting));
        }
        return complete;
    }

    private void requireChair(Evaluation e, List<EvaluationMember> committee, User me, String action) {
        EvaluationMember m = member(committee, me.getId())
                .filter(x -> x.getRole() == CommitteeRole.CHAIR)
                .orElseThrow(() -> new ForbiddenActionException("Only the committee chair can " + action));
        if (!m.isCleared(e.isRequiresCoiDeclaration())) {
            throw new ForbiddenActionException(m.isConflicted()
                    ? "You declared a conflict of interest; the accounting officer must name another chair"
                    : "Make your conflict of interest declaration first");
        }
    }

    private User requireProcurement(String action) {
        User me = currentUser.require();
        Roles.requireAny(me, Roles.PROCUREMENT, action);
        return me;
    }

    private static boolean isProcurement(User me) {
        return me.getRoles().stream().anyMatch(Roles.PROCUREMENT::contains);
    }

    private Tender loadTender(UUID tenantId, UUID tenderId) {
        return tenders.findByTenantIdAndId(tenantId, tenderId)
                .orElseThrow(() -> new NotFoundException("Tender " + tenderId + " not found"));
    }

    private Evaluation lock(UUID tenantId, UUID tenderId) {
        return evaluations.lockByTenantIdAndTenderId(tenantId, tenderId)
                .orElseThrow(() -> new NotFoundException("This tender's evaluation has not been set up"));
    }

    private Map<UUID, User> activeUsers(UUID tenantId, Set<UUID> ids) {
        Map<UUID, User> found = people(tenantId, ids);
        for (UUID id : ids) {
            User u = found.get(id);
            if (u == null || !u.isActive()) {
                throw new BusinessRuleException("User " + id + " is not an active user of this organisation");
            }
        }
        return found;
    }

    private Map<UUID, User> people(UUID tenantId, java.util.Collection<UUID> ids) {
        return currentUser.byIds(tenantId, new HashSet<>(ids)).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private String committeeText(UUID tenantId, List<EvaluationMember> committee) {
        Map<UUID, User> p = people(tenantId, committee.stream().map(EvaluationMember::getUserId).toList());
        return committee.stream().map(m -> name(p, m.getUserId()) + " (" + m.getRole() + ")")
                .collect(Collectors.joining("; "));
    }

    private static Optional<EvaluationMember> member(List<EvaluationMember> committee, UUID userId) {
        return committee.stream().filter(m -> m.getUserId().equals(userId)).findFirst();
    }

    private static UUID chairId(List<EvaluationMember> committee) {
        return committee.stream().filter(m -> m.getRole() == CommitteeRole.CHAIR).map(EvaluationMember::getUserId)
                .findFirst().orElse(null);
    }

    private List<Bid> allBids(Tender t) {
        return bids.findByTenantIdAndTenderIdOrderByReceivedAtAsc(t.getTenantId(), t.getId()).stream()
                .filter(b -> b.getStatus() == BidStatus.RECEIVED).toList();
    }

    /** Preliminary: every received bid. Later stages: the bids still in contention. */
    static List<Bid> stageBids(EvaluationStatus status, List<Bid> received) {
        return received.stream().filter(b -> inStage(status, b)).toList();
    }

    static boolean inStage(EvaluationStatus status, Bid b) {
        return switch (status) {
            case PRELIMINARY -> b.getStatus() == BidStatus.RECEIVED
                    && (b.getDisqualifiedStage() == null || b.getDisqualifiedStage() == EvaluationStage.PRELIMINARY);
            case TECHNICAL, FINANCIAL -> b.isInContention();
            default -> false;
        };
    }

    private static CriterionStage criterionStage(EvaluationStatus s) {
        return s == EvaluationStatus.TECHNICAL ? CriterionStage.TECHNICAL : CriterionStage.PRELIMINARY;
    }

    private List<EvaluationCriterion> crit(Evaluation e) {
        return criteria.findByEvaluationIdOrderByStageAscSortOrderAsc(e.getId());
    }

    private List<EvaluationCriterion> stageCriteria(Evaluation e, CriterionStage stage) {
        return crit(e).stream().filter(c -> c.getStage() == stage)
                .sorted(Comparator.comparingInt(EvaluationCriterion::getSortOrder)).toList();
    }

    private List<Mark> marks(Evaluation e) {
        return scores.findByEvaluationId(e.getId()).stream()
                .map(s -> new Mark(s.getCriterionId(), s.getBidId(), s.getMemberId(), s.getPassed(), s.getScore()))
                .toList();
    }

    private Map<UUID, EvaluationBidResult> resultsByBid(Evaluation e) {
        return results.findByEvaluationId(e.getId()).stream()
                .collect(Collectors.toMap(EvaluationBidResult::getBidId, Function.identity(), (a, b) -> a, HashMap::new));
    }

    private List<EvaluationApproval> currentRound(Evaluation e) {
        return approvals.findByEvaluationIdOrderByReportRoundAscStepOrderAsc(e.getId()).stream()
                .filter(a -> a.getRound() == e.getRound()).toList();
    }

    private void notifyStep(UUID tenantId, Tender t, String role, boolean opinion) {
        notifications.notifyRole(tenantId, role, new Message("evaluation.approval",
                (opinion ? "Professional opinion needed: " : "Award approval needed: ") + t.getNumber(),
                "The evaluation committee's report on \"" + t.getTitle() + "\" is signed and waiting for you.",
                link(t)));
    }

    private static String receiptNo(List<Bid> list, UUID bidId) {
        return list.stream().filter(b -> b.getId().equals(bidId)).map(Bid::getReceiptNo).findFirst().orElse(null);
    }

    private static String link(Tender t) {
        return "/tenders/" + t.getId() + "?tab=evaluation";
    }

    private static String label(EvaluationStatus s) {
        return Evaluation.label(s);
    }

    private static String name(Map<UUID, User> people, UUID id) {
        User u = people.get(id);
        return u == null ? "Unknown user" : u.getDisplayName();
    }

    private static Ref ref(Map<UUID, User> people, UUID id) {
        return id == null ? null : new Ref(id, name(people, id));
    }

    private static BigDecimal technicalTotal(List<EvaluationCriterion> crit) {
        return crit.stream().filter(c -> c.getStage() == CriterionStage.TECHNICAL).map(EvaluationCriterion::getMaxScore)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String cap(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max - 1) + "\u2026";
    }

    private Instant now() {
        return Instant.now(clock);
    }

    private static String money(String currency, long minor) {
        return currency + " " + String.format("%,.2f", minor / 100.0);
    }

    // ================================================================== view

    private EvaluationResponse response(Tender t, Evaluation e, User me) {
        return new EvaluationResponse(true, false, null, List.of(), detail(t, e, me));
    }

    private EvaluationDetail detail(Tender t, Evaluation e, User me) {
        UUID tenantId = t.getTenantId();
        List<EvaluationMember> committee = members.findByEvaluationIdOrderByAddedAtAsc(e.getId());
        List<EvaluationCriterion> crit = crit(e);
        List<EvaluationApproval> steps = approvals.findByEvaluationIdOrderByReportRoundAscStepOrderAsc(e.getId());
        Optional<EvaluationMember> mine = member(committee, me.getId());
        boolean procurement = isProcurement(me);
        Set<String> approverRoles = new HashSet<>(steps.stream().map(EvaluationApproval::getApproverRole).toList());
        approverRoles.add(Roles.ACCOUNTING_OFFICER);
        if (e.getReportReviewerRole() != null) {
            approverRoles.add(e.getReportReviewerRole());
        }
        approverRoles.add(e.getAwardApproverRole());
        if (!procurement && mine.isEmpty() && me.getRoles().stream().noneMatch(approverRoles::contains)) {
            throw new ForbiddenActionException("Only procurement staff, the evaluation committee and the approvers "
                    + "can see this evaluation");
        }

        boolean coi = e.isRequiresCoiDeclaration();
        List<Bid> received = allBids(t);
        List<Bid> stageBids = stageBids(e.getStatus(), received);
        CriterionStage cs = criterionStage(e.getStatus());
        List<EvaluationCriterion> stageCrit = e.getStatus().isScoring()
                ? crit.stream().filter(c -> c.getStage() == cs).toList() : List.of();
        List<EvaluationScore> allScores = scores.findByEvaluationId(e.getId());
        Map<UUID, EvaluationBidResult> res = resultsByBid(e);

        // Progress of the current stage.
        Set<UUID> stageCritIds = stageCrit.stream().map(EvaluationCriterion::getId).collect(Collectors.toSet());
        Set<UUID> stageBidIds = stageBids.stream().map(Bid::getId).collect(Collectors.toSet());
        Map<UUID, Integer> done = new HashMap<>();
        for (EvaluationScore s : allScores) {
            if (stageCritIds.contains(s.getCriterionId()) && stageBidIds.contains(s.getBidId())) {
                done.merge(s.getMemberId(), 1, Integer::sum);
            }
        }
        int needed = stageCrit.size() * stageBids.size();

        Set<UUID> userIds = new HashSet<>();
        committee.forEach(m -> userIds.add(m.getUserId()));
        userIds.add(e.getCreatedBy());
        if (e.getAppointedBy() != null) {
            userIds.add(e.getAppointedBy());
        }
        if (e.getReportSubmittedBy() != null) {
            userIds.add(e.getReportSubmittedBy());
        }
        steps.forEach(a -> {
            if (a.getDecidedBy() != null) {
                userIds.add(a.getDecidedBy());
            }
        });
        Map<UUID, User> people = people(tenantId, userIds);
        Map<UUID, Supplier> supplierMap = suppliers.byIds(tenantId,
                received.stream().map(Bid::getSupplierId).collect(Collectors.toSet()));

        List<MemberView> memberViews = committee.stream().map(m -> new MemberView(m.getUserId(), name(people, m.getUserId()),
                m.getRole(), m.getDeclaredAt(), m.getHasConflict(), m.getDeclaration(), m.signedFor(e.getRound()),
                m.signedFor(e.getRound()) ? m.getSignedAt() : null, m.signedFor(e.getRound()) ? m.getSignNote() : null,
                m.mayScore(coi) ? done.getOrDefault(m.getUserId(), 0) : 0, m.mayScore(coi) ? needed : 0)).toList();

        StageProgress progress = null;
        if (e.getStatus().isScoring()) {
            List<UUID> scoring = committee.stream().filter(m -> m.mayScore(coi)).map(EvaluationMember::getUserId).toList();
            long complete = stageBids.isEmpty() ? scoring.size()
                    : scoring.stream().filter(id -> done.getOrDefault(id, 0) == needed).count();
            String waitingFor = committee.stream()
                    .filter(m -> m.getRole().isVoting() && !m.isConflicted())
                    .filter(m -> !m.mayScore(coi) || done.getOrDefault(m.getUserId(), 0) < needed)
                    .map(m -> name(people, m.getUserId()) + (m.mayScore(coi) ? "" : " (declaration)"))
                    .collect(Collectors.joining(", "));
            progress = new StageProgress(e.getStatus(), stageBids.size(), stageCrit.size(), scoring.size(),
                    (int) complete, e.getQuorum(), complete >= e.getQuorum() || stageBids.isEmpty(),
                    waitingFor.isEmpty() ? null : waitingFor);
        } else if (e.getStatus() == EvaluationStatus.FINANCIAL) {
            long priced = stageBids.stream().filter(b -> res.containsKey(b.getId())
                    && res.get(b.getId()).getEvaluatedPriceMinor() != null).count();
            String waiting = !t.pricesOpen() ? "the financial envelopes to be opened"
                    : priced < stageBids.size() ? "evaluated prices for " + (stageBids.size() - priced) + " bid(s)" : null;
            progress = new StageProgress(e.getStatus(), stageBids.size(), 0, 0, 0, e.getQuorum(), waiting == null, waiting);
        }

        boolean twoEnvelopeHidden = t.isTwoEnvelope();
        List<BidRow> rows = received.stream().map(b -> {
            EvaluationBidResult r = res.get(b.getId());
            Supplier s = supplierMap.get(b.getSupplierId());
            boolean priceVisible = t.pricesOpen() && !(twoEnvelopeHidden && b.getDisqualifiedStage() != null
                    && b.getDisqualifiedStage() != EvaluationStage.FINANCIAL);
            return new BidRow(b.getId(), b.getReceiptNo(), new Ref(b.getSupplierId(), s == null ? "Unknown supplier" : s.getName()),
                    s == null ? null : s.getKraPin(), s == null ? null : s.getAgpoCategory(), stageBidIds.contains(b.getId()),
                    b.getDisqualifiedStage(), b.getDisqualifiedReason(),
                    r == null ? null : r.getPreliminaryPassed(), r == null ? null : r.getPreliminaryNote(),
                    r == null ? null : r.getTechnicalScore(), r == null ? null : r.getTechnicalPct(),
                    r == null ? null : r.getTechnicalPassed(), priceVisible ? b.getReadOutPriceMinor() : null,
                    r == null ? null : r.getEvaluatedPriceMinor(), r == null ? null : r.getPriceNote(),
                    r == null ? null : r.getFinancialRank());
        }).toList();

        // Own marks always; everyone's marks once their stage is closed.
        Set<CriterionStage> closedStages = EnumSet.noneOf(CriterionStage.class);
        if (e.getPreliminaryClosedAt() != null) {
            closedStages.add(CriterionStage.PRELIMINARY);
        }
        if (e.getTechnicalClosedAt() != null) {
            closedStages.add(CriterionStage.TECHNICAL);
        }
        Map<UUID, CriterionStage> stageOf = crit.stream()
                .collect(Collectors.toMap(EvaluationCriterion::getId, EvaluationCriterion::getStage));
        List<MarkView> myMarks = allScores.stream().filter(s -> s.getMemberId().equals(me.getId()))
                .map(EvaluationService::markView).toList();
        List<MarkView> closedMarks = allScores.stream().filter(s -> closedStages.contains(stageOf.get(s.getCriterionId())))
                .map(EvaluationService::markView).toList();

        Set<UUID> signers = EvaluationRules.signatories(e.getReportSignatories(), committee, coi);
        List<EvaluationApproval> round = steps.stream().filter(a -> a.getRound() == e.getRound()).toList();
        Optional<EvaluationApproval> pending = e.getStatus() == EvaluationStatus.APPROVAL
                ? round.stream().filter(a -> a.getStatus() == ApprovalStatus.PENDING).findFirst() : Optional.empty();

        Award award = null;
        if (t.getAwardedBidId() != null) {
            Bid ab = received.stream().filter(b -> b.getId().equals(t.getAwardedBidId())).findFirst().orElse(null);
            Supplier s = ab == null ? null : supplierMap.get(ab.getSupplierId());
            award = new Award(t.getAwardedBidId(), s == null ? null : new Ref(s.getId(), s.getName()),
                    t.getAwardedAmountMinor(), t.getAwardedAt(), t.getStandstillEndsAt());
        }

        List<String> warnings = new ArrayList<>();
        long available = committee.stream().filter(m -> m.getRole().isVoting() && !m.isConflicted()).count();
        if (e.getStatus().isScoring() && available < e.getQuorum()) {
            warnings.add("Only " + available + " member(s) can still take part and the quorum is " + e.getQuorum()
                    + ". The accounting officer must appoint more members.");
        }
        rows.stream().filter(r -> r.rank() != null && r.rank() == 1).map(BidRow::evaluatedPriceMinor)
                .filter(Objects::nonNull).findFirst()
                .filter(lowest -> lowest > t.getEstimatedValueMinor())
                .ifPresent(lowest -> warnings.add("The lowest evaluated price (" + money(t.getCurrency(), lowest)
                        + ") is above the budget (" + money(t.getCurrency(), t.getEstimatedValueMinor()) + ")."));
        if (rows.stream().filter(r -> r.rank() != null && r.rank() == 1).count() > 1) {
            warnings.add("More than one bid has the lowest evaluated price; record how the tie was resolved in the report.");
        }

        boolean chairCleared = mine.filter(m -> m.getRole() == CommitteeRole.CHAIR && m.isCleared(coi)).isPresent();
        boolean isAo = me.hasRole(Roles.ACCOUNTING_OFFICER);
        Me meView = new Me(mine.map(EvaluationMember::getRole).orElse(null),
                mine.map(m -> m.isCleared(coi)).orElse(false),
                mine.filter(m -> m.getDeclaredAt() == null && coi && e.getStatus() != EvaluationStatus.SETUP
                        && !e.getStatus().isFinal()).isPresent(),
                mine.map(m -> m.mayScore(coi)).orElse(false) && e.getStatus().isScoring(),
                mine.filter(m -> m.getRole() == CommitteeRole.CHAIR).isPresent(),
                e.getStatus() == EvaluationStatus.SIGNING && signers.contains(me.getId())
                        && mine.filter(m -> !m.signedFor(e.getRound())).isPresent());
        Permissions can = new Permissions(
                procurement && e.getStatus() == EvaluationStatus.SETUP,
                isAo && e.getStatus() == EvaluationStatus.SETUP && mine.isEmpty(),
                isAo && COMMITTEE_CHANGE_STAGES.contains(e.getStatus()) && mine.isEmpty(),
                mine.filter(m -> m.getDeclaredAt() == null).isPresent() && e.getStatus() != EvaluationStatus.SETUP
                        && !e.getStatus().isFinal(),
                meView.mayScore(),
                chairCleared && (e.getStatus().isScoring() || e.getStatus() == EvaluationStatus.FINANCIAL),
                chairCleared && e.getStatus() == EvaluationStatus.FINANCIAL && t.pricesOpen(),
                chairCleared && e.getStatus() == EvaluationStatus.REPORT,
                meView.mustSign(),
                pending.filter(a -> me.hasRole(a.getApproverRole())).isPresent() && mine.isEmpty(),
                pending.map(EvaluationApproval::isOpinion).orElse(false),
                pending.filter(a -> round.getLast().getId().equals(a.getId())).isPresent());

        Recommendation recommendation = e.getReportSubmittedAt() == null ? null
                : new Recommendation(e.isNoAward(), e.getRecommendedBidId(), e.getRecommendedAmountMinor(),
                        e.getRecommendation(), ref(people, e.getReportSubmittedBy()), e.getReportSubmittedAt(),
                        e.getRound());

        return new EvaluationDetail(e.getId(), t.getId(), e.getStatus(), e.getRound(), e.getTemplateCode(),
                e.getTechnicalPassMarkPct(), e.getMinMembers(), e.getMaxMembers(), e.getQuorum(), coi,
                e.getReportSignatories(), e.getReportReviewerRole(), ref(people, e.getCreatedBy()), e.getCreatedAt(),
                ref(people, e.getAppointedBy()), e.getAppointedAt(), e.getPreliminaryClosedAt(),
                e.getTechnicalClosedAt(), e.getFinancialClosedAt(), e.getDecidedAt(), memberViews,
                crit.stream().sorted(Comparator.comparing(EvaluationCriterion::getStage)
                                .thenComparingInt(EvaluationCriterion::getSortOrder))
                        .map(c -> new CriterionView(c.getId(), c.getStage(), c.getSortOrder(), c.getName(),
                                c.getDescription(), c.getMaxScore())).toList(),
                rows, myMarks, closedMarks, progress, recommendation,
                signers.stream().map(id -> ref(people, id)).toList(),
                steps.stream().map(a -> new ApprovalView(a.getId(), a.getRound(), a.getStepOrder(), a.getApproverRole(),
                        a.isOpinion(), a.getStatus(), ref(people, a.getDecidedBy()), a.getDecidedAt(), a.getNote()))
                        .toList(),
                award, t.getEstimatedValueMinor(), t.getCurrency(), warnings, meView, can, e.getVersion());
    }

    private static MarkView markView(EvaluationScore s) {
        return new MarkView(s.getCriterionId(), s.getBidId(), s.getMemberId(), s.getPassed(), s.getScore(),
                s.getComment());
    }
}
