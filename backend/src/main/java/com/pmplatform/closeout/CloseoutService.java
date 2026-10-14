package com.pmplatform.closeout;

import com.pmplatform.closeout.api.CloseoutDtos.AssetInput;
import com.pmplatform.closeout.api.CloseoutDtos.AssetView;
import com.pmplatform.closeout.api.CloseoutDtos.AutoCheck;
import com.pmplatform.closeout.api.CloseoutDtos.CloseoutView;
import com.pmplatform.closeout.api.CloseoutDtos.ContractLine;
import com.pmplatform.closeout.api.CloseoutDtos.DefectRequest;
import com.pmplatform.closeout.api.CloseoutDtos.DefectView;
import com.pmplatform.closeout.api.CloseoutDtos.DocumentView;
import com.pmplatform.closeout.api.CloseoutDtos.Figures;
import com.pmplatform.closeout.api.CloseoutDtos.HandoverRequest;
import com.pmplatform.closeout.api.CloseoutDtos.HandoverView;
import com.pmplatform.closeout.api.CloseoutDtos.ItemRequest;
import com.pmplatform.closeout.api.CloseoutDtos.ItemView;
import com.pmplatform.closeout.api.CloseoutDtos.Permissions;
import com.pmplatform.contracts.ContractService;
import com.pmplatform.contracts.ContractStatus;
import com.pmplatform.contracts.api.ContractDtos.ContractSummary;
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
import com.pmplatform.portfolio.ProjectPhase;
import com.pmplatform.portfolio.ProjectService;
import com.pmplatform.portfolio.ProjectStatus;
import com.pmplatform.procurement.tender.StoredFile;
import com.pmplatform.procurement.tender.TenderFiles;
import com.pmplatform.procurement.tender.api.TenderDtos.Ref;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
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
import org.springframework.web.multipart.MultipartFile;

/**
 * Closeout and handover of a project: the completion checklist, defects during the defects liability
 * period, the handover of the assets to the operating unit, and closing the project.
 *
 * <p>The project manager or the PMO starts closeout and works through the checklist; the platform
 * also checks that contracts are finished, the schedule is complete and no critical defect is open.
 * The named receiving officer accepts the handover, which freezes the final figures and lets the
 * project pass gate G4. After the defects liability period, with no defect open and all retention
 * released, the PMO lead closes the project.
 */
@Service
public class CloseoutService {

    static final ZoneId LOCAL_ZONE = ZoneId.of("Africa/Nairobi");
    static final int MAX_FILES = 200;

    /** A file returned for download. */
    public record Download(String fileName, String contentType, long sizeBytes, InputStream content) {
    }

    private final CloseoutRepository closeouts;
    private final ChecklistItemRepository items;
    private final DefectRepository defects;
    private final HandoverAssetRepository assets;
    private final CloseoutDocumentRepository documents;
    private final ChecklistTemplateService templates;
    private final LessonService lessons;
    private final ProjectService projects;
    private final ContractService contracts;
    private final PlanningService planning;
    private final TenderFiles files;
    private final CurrentUserService currentUser;
    private final NotificationService notifications;
    private final AuditService audit;
    private final Clock clock;
    private final CloseoutLocks locks;

    public CloseoutService(CloseoutRepository closeouts, ChecklistItemRepository items, DefectRepository defects,
            HandoverAssetRepository assets, CloseoutDocumentRepository documents, ChecklistTemplateService templates,
            LessonService lessons, ProjectService projects, ContractService contracts, PlanningService planning,
            TenderFiles files, CurrentUserService currentUser, NotificationService notifications, AuditService audit,
            Clock clock, CloseoutLocks locks) {
        this.locks = locks;
        this.closeouts = closeouts;
        this.items = items;
        this.defects = defects;
        this.assets = assets;
        this.documents = documents;
        this.templates = templates;
        this.lessons = lessons;
        this.projects = projects;
        this.contracts = contracts;
        this.planning = planning;
        this.files = files;
        this.currentUser = currentUser;
        this.notifications = notifications;
        this.audit = audit;
        this.clock = clock;
    }

    // ================================================================== queries

    @Transactional(readOnly = true)
    public CloseoutView get(UUID tenantId, UUID projectId) {
        Project p = projects.get(tenantId, projectId);
        User me = currentUser.require();
        return closeouts.findByTenantIdAndProjectId(tenantId, projectId).map(c -> view(c, p, me))
                .orElseGet(() -> notStarted(p, me));
    }

    @Transactional(readOnly = true)
    public Download document(UUID tenantId, UUID projectId, UUID documentId) {
        projects.get(tenantId, projectId);
        CloseoutDocument d = doc(tenantId, projectId, documentId);
        return new Download(d.getFileName(), d.getContentType(), d.getSizeBytes(), files.openPlain(d.getStorageKey()));
    }

    // ================================================================== start, handover, close

    /** The handover date once the handover has been accepted (for benefits and review scheduling). */
    @Transactional(readOnly = true)
    public Optional<LocalDate> acceptedHandoverDate(UUID tenantId, UUID projectId) {
        return closeouts.findByTenantIdAndProjectId(tenantId, projectId)
                .filter(c -> c.getStatus() == CloseoutStatus.HANDED_OVER || c.getStatus() == CloseoutStatus.CLOSED)
                .map(Closeout::getHandoverDate);
    }

    @Transactional
    public CloseoutView start(UUID tenantId, UUID projectId) {
        User me = requireEditor("start closeout");
        Project p = projects.get(tenantId, projectId);
        if (p.getStatus() != ProjectStatus.ACTIVE) {
            throw new BusinessRuleException("Project " + p.getCode() + " is " + p.getStatus().name().toLowerCase());
        }
        if (p.getPhase().ordinal() < ProjectPhase.EXECUTION.ordinal()) {
            throw new BusinessRuleException("Closeout starts once the project is in execution");
        }
        if (closeouts.findByTenantIdAndProjectId(tenantId, projectId).isPresent()) {
            throw new BusinessRuleException("Closeout has already been started");
        }
        Instant now = now();
        Closeout c = closeouts.save(new Closeout(tenantId, projectId, me.getId(), now));
        int order = 1;
        for (ChecklistDefaults.Item t : templates.itemsFor(tenantId)) {
            ChecklistItem i = new ChecklistItem(tenantId, projectId, order++, now);
            i.define(t.title(), t.description(), t.responsibleRole(), t.evidenceRequired(), now);
            items.save(i);
        }
        audit.record(tenantId, "closeout.started", "Project", projectId,
                JsonText.object("checklistItems", order - 1));
        return view(c, p, me);
    }

    @Transactional
    public CloseoutView saveHandover(UUID tenantId, UUID projectId, HandoverRequest req) {
        User me = requireEditor("prepare the handover");
        Closeout c = lock(tenantId, projectId);
        c.requireOpen("changing the handover");
        if (req.receivingOfficerId() != null) {
            User officer = currentUser.byIds(tenantId, Set.of(req.receivingOfficerId())).stream().findFirst()
                    .filter(User::isActive)
                    .orElseThrow(() -> new BusinessRuleException("The receiving officer must be an active user"));
            if (officer.getId().equals(me.getId())) {
                throw new BusinessRuleException("Name someone in the receiving unit, not yourself");
            }
        }
        Instant now = now();
        c.planHandover(req.receivingUnit(), req.receivingOfficerId(), req.handoverDate(), req.note(), now);
        assets.deleteAll(assets.findByTenantIdAndProjectIdOrderBySortOrderAsc(tenantId, projectId));
        assets.flush();
        int order = 1;
        for (AssetInput a : req.assets()) {
            assets.save(new HandoverAsset(tenantId, projectId, order++, a.description(), a.quantity(), a.unit(),
                    a.tag(), a.location(), a.valueMinor(), now));
        }
        audit.record(tenantId, "closeout.handover_saved", "Project", projectId,
                JsonText.object("receivingUnit", c.getReceivingUnit(), "receivingOfficer", c.getReceivingOfficerId(),
                        "handoverDate", c.getHandoverDate(), "assets", req.assets().size()));
        return view(c, projects.get(tenantId, projectId), me);
    }

    @Transactional
    public CloseoutView submitHandover(UUID tenantId, UUID projectId) {
        User me = requireEditor("submit the handover");
        Closeout c = lock(tenantId, projectId);
        Project p = projects.get(tenantId, projectId);
        c.requireOpen("submitting the handover");
        List<String> blockers = handoverBlockers(c, p, items.findByTenantIdAndProjectIdOrderBySortOrderAsc(tenantId,
                projectId), autoChecks(p, contractsOf(p), planning.compute(tenantId, projectId),
                        defects.findByTenantIdAndProjectIdOrderByNumberAsc(tenantId, projectId)));
        if (!blockers.isEmpty()) {
            throw new BusinessRuleException("The handover cannot be submitted yet: " + String.join("; ", blockers));
        }
        c.submitHandover(me.getId(), now());
        audit.record(tenantId, "closeout.handover_submitted", "Project", projectId,
                JsonText.object("receivingUnit", c.getReceivingUnit(), "receivingOfficer", c.getReceivingOfficerId(),
                        "handoverDate", c.getHandoverDate()));
        notifications.notifyUsers(tenantId, List.of(c.getReceivingOfficerId()), new Message("closeout.handover",
                LessonService.cap(p.getCode() + " is ready to hand over to " + c.getReceivingUnit(), 200),
                "Check the assets and documents, then accept or reject the handover.", link(p)));
        return view(c, p, me);
    }

    @Transactional
    public CloseoutView acceptHandover(UUID tenantId, UUID projectId, String note) {
        User me = currentUser.require();
        Closeout c = lock(tenantId, projectId);
        Project p = projects.get(tenantId, projectId);
        List<ContractSummary> cs = contractsOf(p);
        Optional<Result> schedule = planning.compute(tenantId, projectId);
        if (c.getStatus() == CloseoutStatus.HANDOVER_SUBMITTED && me.getId().equals(c.getReceivingOfficerId())) {
            // Things can change after submission (a critical defect, a new contract): check again.
            List<String> blockers = handoverBlockers(c, p,
                    items.findByTenantIdAndProjectIdOrderBySortOrderAsc(tenantId, projectId),
                    autoChecks(p, cs, schedule, defects.findByTenantIdAndProjectIdOrderByNumberAsc(tenantId, projectId)));
            if (!blockers.isEmpty()) {
                throw new BusinessRuleException("The handover can no longer be accepted as submitted: "
                        + String.join("; ", blockers) + ". Reject it so the project team can deal with this.");
            }
        }
        Figures f = liveFigures(p, cs, schedule);
        c.acceptHandover(me.getId(), note, new Closeout.Figures(f.budgetMinor(), f.contractValueMinor(),
                f.certifiedMinor(), f.paidMinor(), f.baselineFinish(), f.actualFinish(), f.earnedPct(), f.dlpEnd()),
                now());
        audit.record(tenantId, "closeout.handover_accepted", "Project", projectId,
                JsonText.object("receivingUnit", c.getReceivingUnit(), "note", c.getHandoverDecisionNote(),
                        "budgetMinor", f.budgetMinor(), "certifiedMinor", f.certifiedMinor(),
                        "actualFinish", f.actualFinish(), "dlpEnd", f.dlpEnd()));
        tellTeam(tenantId, c, me, new Message("closeout.handover_accepted",
                LessonService.cap(p.getCode() + " handed over to " + c.getReceivingUnit(), 200),
                "Accepted by " + me.getDisplayName() + ". The project can now pass gate G4.", link(p)));
        return view(c, p, me);
    }

    @Transactional
    public CloseoutView rejectHandover(UUID tenantId, UUID projectId, String note) {
        User me = currentUser.require();
        Closeout c = lock(tenantId, projectId);
        Project p = projects.get(tenantId, projectId);
        c.rejectHandover(me.getId(), note, now());
        audit.record(tenantId, "closeout.handover_rejected", "Project", projectId, JsonText.object("note", note));
        tellTeam(tenantId, c, me, new Message("closeout.handover_rejected",
                LessonService.cap(c.getReceivingUnit() + " did not accept the " + p.getCode() + " handover", 200),
                LessonService.cap(note.trim(), 1000), link(p)));
        return view(c, p, me);
    }

    @Transactional
    public CloseoutView close(UUID tenantId, UUID projectId, String note) {
        User me = currentUser.require();
        Roles.requireAny(me, Set.of(Roles.PMO_LEAD), "close projects");
        Closeout c = lock(tenantId, projectId);
        Project p = projects.get(tenantId, projectId);
        List<String> blockers = closeBlockers(c, p, contractsOf(p),
                defects.findByTenantIdAndProjectIdOrderByNumberAsc(tenantId, projectId));
        if (!blockers.isEmpty()) {
            throw new BusinessRuleException("The project cannot be closed yet: " + String.join("; ", blockers));
        }
        c.close(me.getId(), note, now());
        projects.close(tenantId, projectId, note);
        audit.record(tenantId, "closeout.closed", "Project", projectId, JsonText.object("note", c.getClosureNote()));
        return view(c, p, me);
    }

    // ================================================================== checklist

    @Transactional
    public CloseoutView addItem(UUID tenantId, UUID projectId, ItemRequest req) {
        User me = requireEditor("change the checklist");
        Closeout c = lock(tenantId, projectId);
        c.requireOpen("changing the checklist");
        int order = items.findByTenantIdAndProjectIdOrderBySortOrderAsc(tenantId, projectId).stream()
                .mapToInt(ChecklistItem::getSortOrder).max().orElse(0) + 1;
        Instant now = now();
        ChecklistItem i = new ChecklistItem(tenantId, projectId, order, now);
        i.define(req.title(), req.description(), req.responsibleRole(), Boolean.TRUE.equals(req.evidenceRequired()),
                now);
        items.save(i);
        audit.record(tenantId, "closeout.item_added", "Project", projectId, JsonText.object("item", i.getTitle()));
        return view(c, projects.get(tenantId, projectId), me);
    }

    @Transactional
    public CloseoutView editItem(UUID tenantId, UUID projectId, UUID itemId, ItemRequest req) {
        User me = requireEditor("change the checklist");
        Closeout c = lock(tenantId, projectId);
        c.requireOpen("changing the checklist");
        ChecklistItem i = item(tenantId, projectId, itemId);
        if (!i.isOpen()) {
            throw new BusinessRuleException("Reopen \"" + i.getTitle() + "\" before changing it");
        }
        i.define(req.title(), req.description(), req.responsibleRole(), Boolean.TRUE.equals(req.evidenceRequired()),
                now());
        audit.record(tenantId, "closeout.item_changed", "Project", projectId, JsonText.object("item", i.getTitle()));
        return view(c, projects.get(tenantId, projectId), me);
    }

    @Transactional
    public CloseoutView deleteItem(UUID tenantId, UUID projectId, UUID itemId) {
        User me = requireEditor("change the checklist");
        Closeout c = lock(tenantId, projectId);
        c.requireOpen("changing the checklist");
        ChecklistItem i = item(tenantId, projectId, itemId);
        if (!i.isOpen()) {
            throw new BusinessRuleException("\"" + i.getTitle() + "\" is already done; mark it not applicable "
                    + "instead of removing it");
        }
        if (documents.findByTenantIdAndProjectIdOrderByUploadedAtAsc(tenantId, projectId).stream()
                .anyMatch(d -> itemId.equals(d.getRefId()))) {
            throw new BusinessRuleException("\"" + i.getTitle() + "\" has evidence attached; remove it first");
        }
        items.delete(i);
        audit.record(tenantId, "closeout.item_removed", "Project", projectId, JsonText.object("item", i.getTitle()));
        return view(c, projects.get(tenantId, projectId), me);
    }

    @Transactional
    public CloseoutView markItem(UUID tenantId, UUID projectId, UUID itemId, ItemStatus to, String note) {
        User me = currentUser.require();
        Closeout c = lock(tenantId, projectId);
        c.requireOpen("changing the checklist");
        ChecklistItem i = item(tenantId, projectId, itemId);
        if (!canMark(i, me)) {
            throw new ForbiddenActionException("The project manager, the PMO or the role responsible ("
                    + i.getResponsibleRole() + ") marks this item");
        }
        Instant now = now();
        switch (to) {
            case DONE -> {
                int evidence = (int) documents.findByTenantIdAndProjectIdOrderByUploadedAtAsc(tenantId, projectId)
                        .stream().filter(d -> d.getKind() == CloseoutDocumentKind.EVIDENCE && itemId.equals(d.getRefId()))
                        .count();
                i.done(me.getId(), note, evidence, now);
            }
            case NOT_APPLICABLE -> i.notApplicable(me.getId(), note, now);
            case OPEN -> i.reopen(now);
        }
        audit.record(tenantId, "closeout.item_" + to.name().toLowerCase(), "Project", projectId,
                JsonText.object("item", i.getTitle(), "note", i.getNote()));
        return view(c, projects.get(tenantId, projectId), me);
    }

    // ================================================================== defects

    @Transactional
    public CloseoutView reportDefect(UUID tenantId, UUID projectId, DefectRequest req) {
        User me = currentUser.require();
        Closeout c = lock(tenantId, projectId);
        if (c.getStatus() == CloseoutStatus.CLOSED) {
            throw new BusinessRuleException("The project is closed");
        }
        Project p = projects.get(tenantId, projectId);
        Optional<ContractSummary> contract = checkContract(p, req.contractId());
        locks.contract(req.contractId());
        int number = defects.findByTenantIdAndProjectIdOrderByNumberAsc(tenantId, projectId).stream()
                .mapToInt(Defect::getNumber).max().orElse(0) + 1;
        Instant now = now();
        Defect d = new Defect(tenantId, projectId, number, me.getId(), now);
        d.describe(req.contractId(), req.title(), req.description(), req.location(), req.severity(), req.dueDate(), now);
        defects.save(d);
        c.changed(now);
        audit.record(tenantId, "closeout.defect_reported", "Project", projectId,
                JsonText.object("defect", "D" + number, "title", d.getTitle(), "severity", d.getSeverity(),
                        "contract", contract.map(ContractSummary::number).orElse(null)));
        Message m = new Message("closeout.defect", LessonService.cap(p.getCode() + " defect D" + number + " ("
                + d.getSeverity().name().toLowerCase() + "): " + d.getTitle(), 200),
                d.getDueDate() == null ? "Reported by " + me.getDisplayName() : "To be put right by " + d.getDueDate(),
                link(p));
        contract.map(ContractSummary::contractManager).filter(Objects::nonNull).map(Ref::id)
                .filter(id -> !id.equals(me.getId()))
                .ifPresent(id -> notifications.notifyUsers(tenantId, List.of(id), m));
        if (d.getSeverity() == DefectSeverity.CRITICAL) {
            notifications.notifyRole(tenantId, Roles.PMO_LEAD, m);
        }
        return view(c, p, me);
    }

    @Transactional
    public CloseoutView editDefect(UUID tenantId, UUID projectId, UUID defectId, DefectRequest req) {
        User me = currentUser.require();
        Closeout c = lock(tenantId, projectId);
        Project p = projects.get(tenantId, projectId);
        Defect d = defect(tenantId, projectId, defectId);
        if (!isEditor(me) && !me.getId().equals(d.getReportedBy())) {
            throw new ForbiddenActionException("Whoever reported the defect, the project manager or the PMO edits it");
        }
        checkContract(p, req.contractId());
        boolean weightChanged = req.severity() != d.getSeverity() || !Objects.equals(req.contractId(), d.getContractId());
        if (weightChanged && !isEditor(me)) {
            // Severity and contract decide what the defect holds back (handover, retention).
            throw new ForbiddenActionException("Only the project manager or the PMO changes a defect's severity or "
                    + "contract");
        }
        locks.contract(d.getContractId());
        locks.contract(req.contractId());
        d.describe(req.contractId(), req.title(), req.description(), req.location(), req.severity(), req.dueDate(),
                now());
        audit.record(tenantId, "closeout.defect_changed", "Project", projectId,
                JsonText.object("defect", "D" + d.getNumber(), "title", d.getTitle(), "severity", d.getSeverity()));
        return view(c, p, me);
    }

    @Transactional
    public CloseoutView defectAction(UUID tenantId, UUID projectId, UUID defectId, String action, String note) {
        User me = currentUser.require();
        Closeout c = lock(tenantId, projectId);
        Project p = projects.get(tenantId, projectId);
        Defect d = defect(tenantId, projectId, defectId);
        Instant now = now();
        switch (action) {
            case "rectified" -> {
                if (!canRectify(d, me, contractsOf(p))) {
                    throw new ForbiddenActionException("The project manager, the PMO or the contract manager records "
                            + "that a defect was put right");
                }
                d.rectified(me.getId(), note, now);
                if (!d.getReportedBy().equals(me.getId())) {
                    notifications.notifyUsers(tenantId, List.of(d.getReportedBy()), new Message("closeout.defect",
                            LessonService.cap(p.getCode() + " defect D" + d.getNumber() + " put right", 200),
                            "Check the work and close the defect, or reopen it.", link(p)));
                }
            }
            case "verify" -> {
                requireVerifier(c, me);
                d.verify(me.getId(), note, now);
            }
            case "reopen" -> {
                requireVerifier(c, me);
                d.reopen(note, now);
            }
            case "waive" -> {
                Roles.requireAny(me, Set.of(Roles.PMO_LEAD), "waive defects");
                d.waive(me.getId(), note, now);
            }
            default -> throw new NotFoundException("Unknown action " + action);
        }
        c.changed(now);
        audit.record(tenantId, "closeout.defect_" + action, "Project", projectId,
                JsonText.object("defect", "D" + d.getNumber(), "status", d.getStatus(), "note", note));
        return view(c, p, me);
    }

    // ================================================================== documents

    @Transactional
    public CloseoutView upload(UUID tenantId, UUID projectId, CloseoutDocumentKind kind, UUID refId,
            MultipartFile file) {
        User me = currentUser.require();
        Closeout c = lock(tenantId, projectId);
        Project p = projects.get(tenantId, projectId);
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("Choose the file");
        }
        if (documents.findByTenantIdAndProjectIdOrderByUploadedAtAsc(tenantId, projectId).size() >= MAX_FILES) {
            throw new BusinessRuleException("A closeout holds at most " + MAX_FILES + " files");
        }
        switch (kind) {
            case EVIDENCE -> {
                c.requireOpen("adding evidence");
                ChecklistItem i = item(tenantId, projectId, refId);
                if (!canMark(i, me)) {
                    throw new ForbiddenActionException("The project manager, the PMO or the role responsible adds "
                            + "evidence for this item");
                }
                if (!i.isOpen()) {
                    throw new BusinessRuleException("Reopen \"" + i.getTitle() + "\" to add evidence");
                }
            }
            case DEFECT -> {
                Defect d = defect(tenantId, projectId, refId);
                if (!d.getStatus().isOutstanding()) {
                    throw new BusinessRuleException("D" + d.getNumber() + " is " + d.getStatus().name().toLowerCase());
                }
            }
            case HANDOVER -> {
                requireEditor("add handover documents");
                c.requireOpen("adding handover documents");
                if (refId != null) {
                    throw new BusinessRuleException("Handover documents belong to the whole handover");
                }
            }
        }
        String key = "closeout/" + tenantId + "/" + projectId + "/" + kind.name().toLowerCase() + "/" + UUID.randomUUID();
        StoredFile stored = files.storePlain(key, file);
        CloseoutDocument d;
        try {
            d = documents.save(new CloseoutDocument(tenantId, projectId, kind, refId, stored, me.getId(), now()));
        } catch (RuntimeException e) {
            files.deleteQuietly(key);
            throw e;
        }
        c.changed(now());
        audit.record(tenantId, "closeout.document_added", "Project", projectId,
                JsonText.object("kind", kind, "refId", refId, "fileName", d.getFileName(), "sha256", d.getSha256()));
        return view(c, p, me);
    }

    @Transactional
    public CloseoutView deleteDocument(UUID tenantId, UUID projectId, UUID documentId) {
        User me = currentUser.require();
        Closeout c = lock(tenantId, projectId);
        Project p = projects.get(tenantId, projectId);
        CloseoutDocument d = doc(tenantId, projectId, documentId);
        if (!canDeleteDocument(d, c, me, items.findByTenantIdAndProjectIdOrderBySortOrderAsc(tenantId, projectId),
                defects.findByTenantIdAndProjectIdOrderByNumberAsc(tenantId, projectId))) {
            throw new BusinessRuleException("This file can no longer be removed");
        }
        documents.delete(d);
        files.deleteQuietly(d.getStorageKey());
        audit.record(tenantId, "closeout.document_removed", "Project", projectId,
                JsonText.object("kind", d.getKind(), "fileName", d.getFileName(), "sha256", d.getSha256()));
        return view(c, p, me);
    }

    // ================================================================== rules

    private List<AutoCheck> autoChecks(Project p, List<ContractSummary> cs, Optional<Result> schedule,
            List<Defect> ds) {
        List<AutoCheck> out = new ArrayList<>();
        List<String> unfinished = cs.stream()
                .filter(x -> x.status() == ContractStatus.DRAFT || x.status() == ContractStatus.ACTIVE)
                .map(x -> x.number() + " (" + x.status().name().toLowerCase() + ")").toList();
        out.add(new AutoCheck("contracts", "Every contract completed or ended", unfinished.isEmpty(),
                cs.isEmpty() ? "The project has no contracts"
                        : unfinished.isEmpty() ? cs.size() + " contract" + (cs.size() == 1 ? "" : "s") + " finished"
                                : "Still open: " + String.join(", ", unfinished)));
        if (schedule.isPresent()) {
            BigDecimal earned = schedule.get().earnedPct();
            boolean done = earned.compareTo(BigDecimal.valueOf(100)) >= 0;
            out.add(new AutoCheck("schedule", "Every scheduled activity finished", done,
                    done ? "Schedule 100% complete" : "Schedule " + earned.stripTrailingZeros().toPlainString()
                            + "% complete"));
        } else {
            out.add(new AutoCheck("schedule", "Every scheduled activity finished", true, "The project has no schedule"));
        }
        long critical = ds.stream().filter(d -> d.getSeverity() == DefectSeverity.CRITICAL && d.getStatus().isOutstanding())
                .count();
        out.add(new AutoCheck("defects", "No critical defect outstanding", critical == 0,
                critical == 0 ? "None" : critical + " critical defect" + (critical == 1 ? "" : "s") + " outstanding"));
        return out;
    }

    private static List<String> handoverBlockers(Closeout c, Project p, List<ChecklistItem> list,
            List<AutoCheck> checks) {
        List<String> out = new ArrayList<>();
        long open = list.stream().filter(ChecklistItem::isOpen).count();
        if (open > 0) {
            out.add(open + " checklist item" + (open == 1 ? " is" : "s are") + " still open");
        }
        checks.stream().filter(a -> !a.passed()).forEach(a -> out.add(a.label().toLowerCase() + ": " + a.detail()));
        if (c.getReceivingUnit() == null || c.getReceivingOfficerId() == null || c.getHandoverDate() == null) {
            out.add("name the receiving unit, the receiving officer and the handover date");
        }
        if (p.getStatus() != ProjectStatus.ACTIVE) {
            out.add("the project is " + p.getStatus().name().toLowerCase());
        }
        return out;
    }

    private List<String> closeBlockers(Closeout c, Project p, List<ContractSummary> cs, List<Defect> ds) {
        List<String> out = new ArrayList<>();
        if (c.getStatus() != CloseoutStatus.HANDED_OVER) {
            out.add(c.getStatus() == CloseoutStatus.CLOSED ? "the project is already closed"
                    : "the handover has not been accepted");
            return out;
        }
        if (p.getPhase() != ProjectPhase.IMPLEMENTATION) {
            out.add("the project must pass gate G4 (formal closure) first");
        }
        long open = ds.stream().filter(d -> d.getStatus().isOutstanding()).count();
        if (open > 0) {
            out.add(open + " defect" + (open == 1 ? " is" : "s are") + " still open");
        }
        LocalDate dlpEnd = dlpEnd(cs);
        if (dlpEnd != null && today().isBefore(dlpEnd)) {
            out.add("the defects liability period runs until " + dlpEnd);
        }
        // Only completed contracts can release retention; a terminated contract's retention is settled outside
        // the platform and does not hold the project open.
        List<String> retention = cs.stream()
                .filter(x -> x.status() == ContractStatus.COMPLETED && x.retentionHeldMinor() > 0)
                .map(ContractSummary::number).toList();
        if (!retention.isEmpty()) {
            out.add("retention is still held on " + String.join(", ", retention));
        }
        cs.stream().filter(x -> x.status() == ContractStatus.DRAFT || x.status() == ContractStatus.ACTIVE)
                .forEach(x -> out.add(x.number() + " is still " + x.status().name().toLowerCase()));
        return out;
    }

    private Figures liveFigures(Project p, List<ContractSummary> cs, Optional<Result> schedule) {
        List<ContractSummary> counted = cs.stream()
                .filter(x -> x.status() != ContractStatus.DRAFT && x.status() != ContractStatus.CANCELLED)
                .filter(x -> x.currency().equals(p.getCurrency())).toList();
        long value = counted.stream().mapToLong(ContractSummary::currentValueMinor).sum();
        long certified = counted.stream().mapToLong(ContractSummary::certifiedMinor).sum();
        long paid = counted.stream().mapToLong(ContractSummary::paidMinor).sum();
        LocalDate baseline = schedule.map(Result::baselineFinish).orElse(null);
        LocalDate finish = schedule.map(Result::forecastFinish).orElse(null);
        Integer slip = baseline == null || finish == null ? null : (int) ChronoUnit.DAYS.between(baseline, finish);
        return new Figures(false, p.getBudgetMinor(), value, certified, paid, p.getBudgetMinor() - certified, baseline,
                finish, slip, schedule.map(Result::earnedPct).orElse(null), dlpEnd(cs), contractLines(cs),
                p.getCurrency());
    }

    private static List<ContractLine> contractLines(List<ContractSummary> cs) {
        return cs.stream().filter(x -> x.status() != ContractStatus.CANCELLED)
                .map(x -> new ContractLine(x.id(), x.number(), x.title(), x.status().name(), x.currentValueMinor(),
                        x.paidMinor(), x.retentionHeldMinor(), x.defectsLiabilityEnds()))
                .toList();
    }

    private static LocalDate dlpEnd(List<ContractSummary> cs) {
        return cs.stream().map(ContractSummary::defectsLiabilityEnds).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(null);
    }

    // ================================================================== view

    private CloseoutView notStarted(Project p, User me) {
        boolean canStart = isEditor(me) && p.getStatus() == ProjectStatus.ACTIVE
                && p.getPhase().ordinal() >= ProjectPhase.EXECUTION.ordinal();
        return new CloseoutView(false, p.getId(), p.getPhase(), null, null, null, List.of(), List.of(), List.of(), null,
                null, lessons.forProject(p.getTenantId(), p.getId(), me), List.of(), List.of(), List.of(), null, null,
                null, new Permissions(canStart, false, false, false, false, false, true), 0);
    }

    private CloseoutView view(Closeout c, Project p, User me) {
        UUID tenantId = c.getTenantId();
        UUID projectId = c.getProjectId();
        List<ChecklistItem> list = items.findByTenantIdAndProjectIdOrderBySortOrderAsc(tenantId, projectId);
        List<Defect> ds = defects.findByTenantIdAndProjectIdOrderByNumberAsc(tenantId, projectId);
        List<HandoverAsset> as = assets.findByTenantIdAndProjectIdOrderBySortOrderAsc(tenantId, projectId);
        List<CloseoutDocument> docs = documents.findByTenantIdAndProjectIdOrderByUploadedAtAsc(tenantId, projectId);
        List<ContractSummary> cs = contractsOf(p);
        Optional<Result> schedule = planning.compute(tenantId, projectId);
        LocalDate today = today();

        Set<UUID> ids = new HashSet<>();
        ids.add(c.getStartedBy());
        ids.add(c.getReceivingOfficerId());
        ids.add(c.getHandoverSubmittedBy());
        ids.add(c.getHandoverDecidedBy());
        ids.add(c.getClosedBy());
        list.forEach(i -> ids.add(i.getDoneBy()));
        ds.forEach(d -> {
            ids.add(d.getReportedBy());
            ids.add(d.getRectifiedBy());
            ids.add(d.getClosedBy());
        });
        docs.forEach(d -> ids.add(d.getUploadedBy()));
        Map<UUID, User> people = people(tenantId, ids);
        Map<UUID, String> contractNumbers = cs.stream().collect(Collectors.toMap(ContractSummary::id,
                ContractSummary::number, (a, b) -> a));

        Function<CloseoutDocument, DocumentView> docView = d -> new DocumentView(d.getId(), d.getKind(), d.getRefId(),
                d.getFileName(), d.getContentType(), d.getSizeBytes(), d.getSha256(), ref(people, d.getUploadedBy()),
                d.getUploadedAt(), canDeleteDocument(d, c, me, list, ds));
        Map<UUID, List<DocumentView>> byRef = docs.stream().filter(d -> d.getRefId() != null)
                .collect(Collectors.groupingBy(CloseoutDocument::getRefId, Collectors.mapping(docView, Collectors.toList())));

        boolean editor = isEditor(me);
        List<ItemView> itemViews = list.stream().map(i -> new ItemView(i.getId(), i.getSortOrder(), i.getTitle(),
                i.getDescription(), i.getResponsibleRole(), i.isEvidenceRequired(), i.getStatus(), i.getNote(),
                ref(people, i.getDoneBy()), i.getDoneAt(), byRef.getOrDefault(i.getId(), List.of()),
                c.isOpen() && canMark(i, me), c.isOpen() && editor && i.isOpen(), i.getVersion())).toList();
        List<DefectView> defectViews = ds.stream().map(d -> new DefectView(d.getId(), d.getNumber(), d.getTitle(),
                d.getDescription(), d.getLocation(), d.getSeverity(), d.getStatus(), d.getContractId(),
                d.getContractId() == null ? null : contractNumbers.get(d.getContractId()), d.getDueDate(),
                d.isOverdue(today), ref(people, d.getReportedBy()), d.getReportedAt(), ref(people, d.getRectifiedBy()),
                d.getRectifiedAt(), d.getRectifiedNote(), ref(people, d.getClosedBy()), d.getClosedAt(),
                d.getClosedNote(), byRef.getOrDefault(d.getId(), List.of()),
                d.getStatus().isOutstanding() && (editor || me.getId().equals(d.getReportedBy())),
                d.getStatus() == DefectStatus.OPEN && canRectify(d, me, cs),
                d.getStatus() == DefectStatus.RECTIFIED && isVerifier(c, me) && !me.getId().equals(d.getRectifiedBy()),
                d.getStatus().isOutstanding() && me.hasRole(Roles.PMO_LEAD), d.getVersion())).toList();

        HandoverView handover = new HandoverView(c.getReceivingUnit(), ref(people, c.getReceivingOfficerId()),
                c.getHandoverDate(), c.getHandoverNote(),
                as.stream().map(a -> new AssetView(a.getId(), a.getSortOrder(), a.getDescription(), a.getQuantity(),
                        a.getUnit(), a.getTag(), a.getLocation(), a.getValueMinor())).toList(),
                as.stream().map(HandoverAsset::getValueMinor).filter(Objects::nonNull).mapToLong(Long::longValue).sum(),
                docs.stream().filter(d -> d.getKind() == CloseoutDocumentKind.HANDOVER).map(docView).toList(),
                ref(people, c.getHandoverSubmittedBy()), c.getHandoverSubmittedAt(), ref(people, c.getHandoverDecidedBy()),
                c.getHandoverDecidedAt(), c.getHandoverDecisionNote());

        Figures live = liveFigures(p, cs, schedule);
        Closeout.Figures frozen = c.frozenFigures();
        Figures figures = frozen == null ? live : new Figures(true, frozen.budgetMinor(), frozen.contractValueMinor(),
                frozen.certifiedMinor(), frozen.paidMinor(), frozen.budgetMinor() - frozen.certifiedMinor(),
                frozen.baselineFinish(), frozen.actualFinish(),
                frozen.baselineFinish() == null || frozen.actualFinish() == null ? null
                        : (int) ChronoUnit.DAYS.between(frozen.baselineFinish(), frozen.actualFinish()),
                frozen.earnedPct(), live.dlpEnd() != null ? live.dlpEnd() : frozen.dlpEnd(), live.contracts(),
                p.getCurrency());

        List<AutoCheck> checks = autoChecks(p, cs, schedule, ds);
        List<String> handoverBlockers = c.isOpen() ? handoverBlockers(c, p, list, checks) : List.of();
        List<String> closeBlockers = c.getStatus() == CloseoutStatus.HANDED_OVER ? closeBlockers(c, p, cs, ds)
                : List.of();
        var lessonViews = lessons.forProject(tenantId, projectId, me);
        List<String> warnings = new ArrayList<>();
        long overdue = ds.stream().filter(d -> d.isOverdue(today)).count();
        if (overdue > 0) {
            warnings.add(overdue + " defect" + (overdue == 1 ? " is" : "s are") + " past the date set to put it right");
        }
        if (c.getStatus() == CloseoutStatus.HANDED_OVER && lessonViews.isEmpty()) {
            warnings.add("No lessons learned have been recorded for this project");
        }
        if (c.getStatus() == CloseoutStatus.HANDED_OVER && p.getPhase() == ProjectPhase.CLOSEOUT) {
            warnings.add("The handover is accepted: pass gate G4 (formal closure) on the Overview tab");
        }

        Permissions can = new Permissions(false, editor && c.isOpen(),
                editor && c.isOpen() && handoverBlockers.isEmpty(),
                c.getStatus() == CloseoutStatus.HANDOVER_SUBMITTED && me.getId().equals(c.getReceivingOfficerId()),
                me.hasRole(Roles.PMO_LEAD) && c.getStatus() == CloseoutStatus.HANDED_OVER && closeBlockers.isEmpty(),
                c.getStatus() != CloseoutStatus.CLOSED, true);
        return new CloseoutView(true, projectId, p.getPhase(), c.getStatus(), ref(people, c.getStartedBy()),
                c.getStartedAt(), itemViews, checks, defectViews, handover, figures, lessonViews, handoverBlockers,
                closeBlockers, warnings, ref(people, c.getClosedBy()), c.getClosedAt(), c.getClosureNote(), can,
                c.getVersion());
    }

    // ================================================================== helpers

    private List<ContractSummary> contractsOf(Project p) {
        return contracts.list(p.getTenantId(), p.getId(), null);
    }

    private Optional<ContractSummary> checkContract(Project p, UUID contractId) {
        if (contractId == null) {
            return Optional.empty();
        }
        return Optional.of(contractsOf(p).stream().filter(x -> x.id().equals(contractId)).findFirst()
                .orElseThrow(() -> new BusinessRuleException("That contract is not on project " + p.getCode())));
    }

    private boolean canMark(ChecklistItem i, User me) {
        return isEditor(me) || (i.getResponsibleRole() != null && me.hasRole(i.getResponsibleRole()));
    }

    private boolean canRectify(Defect d, User me, List<ContractSummary> cs) {
        if (isEditor(me)) {
            return true;
        }
        return d.getContractId() != null && cs.stream().anyMatch(x -> x.id().equals(d.getContractId())
                && x.contractManager() != null && me.getId().equals(x.contractManager().id()));
    }

    private boolean isVerifier(Closeout c, User me) {
        return isEditor(me) || me.getId().equals(c.getReceivingOfficerId());
    }

    private void requireVerifier(Closeout c, User me) {
        if (!isVerifier(c, me)) {
            throw new ForbiddenActionException("The project manager, the PMO or the receiving officer checks defects");
        }
    }

    private boolean canDeleteDocument(CloseoutDocument d, Closeout c, User me, List<ChecklistItem> list,
            List<Defect> ds) {
        if (!me.getId().equals(d.getUploadedBy()) && !isEditor(me)) {
            return false;
        }
        return switch (d.getKind()) {
            case EVIDENCE -> c.isOpen() && list.stream().anyMatch(i -> i.getId().equals(d.getRefId()) && i.isOpen());
            case HANDOVER -> c.isOpen();
            case DEFECT -> ds.stream().anyMatch(x -> x.getId().equals(d.getRefId()) && x.getStatus().isOutstanding());
        };
    }

    private void tellTeam(UUID tenantId, Closeout c, User me, Message m) {
        Set<UUID> to = new HashSet<>();
        to.add(c.getStartedBy());
        if (c.getHandoverSubmittedBy() != null) {
            to.add(c.getHandoverSubmittedBy());
        }
        to.remove(me.getId());
        notifications.notifyUsers(tenantId, to, m);
        notifications.notifyRole(tenantId, Roles.PMO_LEAD, m);
    }

    private Closeout lock(UUID tenantId, UUID projectId) {
        return closeouts.lock(tenantId, projectId)
                .orElseThrow(() -> new NotFoundException("Closeout has not been started for this project"));
    }

    private ChecklistItem item(UUID tenantId, UUID projectId, UUID itemId) {
        return items.findById(itemId == null ? new UUID(0, 0) : itemId)
                .filter(i -> i.getTenantId().equals(tenantId) && i.getProjectId().equals(projectId))
                .orElseThrow(() -> new NotFoundException("Checklist item not found"));
    }

    private Defect defect(UUID tenantId, UUID projectId, UUID defectId) {
        return defects.findById(defectId == null ? new UUID(0, 0) : defectId)
                .filter(d -> d.getTenantId().equals(tenantId) && d.getProjectId().equals(projectId))
                .orElseThrow(() -> new NotFoundException("Defect not found"));
    }

    private CloseoutDocument doc(UUID tenantId, UUID projectId, UUID documentId) {
        return documents.findById(documentId)
                .filter(d -> d.getTenantId().equals(tenantId) && d.getProjectId().equals(projectId))
                .orElseThrow(() -> new NotFoundException("File not found"));
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
        return LessonService.ref(people, id);
    }

    private static String link(Project p) {
        return "/projects/" + p.getId() + "?tab=closeout";
    }

    private Instant now() {
        return Instant.now(clock);
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(LOCAL_ZONE));
    }
}
