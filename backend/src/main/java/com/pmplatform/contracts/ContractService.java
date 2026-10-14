package com.pmplatform.contracts;

import com.pmplatform.contracts.api.ContractDtos.AcceptMilestoneRequest;
import com.pmplatform.contracts.api.ContractDtos.CompleteRequest;
import com.pmplatform.contracts.api.ContractDtos.ContractDetail;
import com.pmplatform.contracts.api.ContractDtos.ContractSummary;
import com.pmplatform.contracts.api.ContractDtos.DocumentView;
import com.pmplatform.contracts.api.ContractDtos.EditContractRequest;
import com.pmplatform.contracts.api.ContractDtos.InvoiceView;
import com.pmplatform.contracts.api.ContractDtos.MilestoneRequest;
import com.pmplatform.contracts.api.ContractDtos.MilestoneView;
import com.pmplatform.contracts.api.ContractDtos.Permissions;
import com.pmplatform.contracts.api.ContractDtos.Rating;
import com.pmplatform.contracts.api.ContractDtos.SecurityView;
import com.pmplatform.contracts.api.ContractDtos.SignRequest;
import com.pmplatform.contracts.api.ContractDtos.StepView;
import com.pmplatform.contracts.api.ContractDtos.SupplierPerformance;
import com.pmplatform.contracts.api.ContractDtos.Totals;
import com.pmplatform.contracts.api.ContractDtos.VariationRequest;
import com.pmplatform.contracts.api.ContractDtos.VariationView;
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
import com.pmplatform.procurement.config.ApprovalStage;
import com.pmplatform.procurement.config.ProcurementRules.ApprovalStep;
import com.pmplatform.procurement.config.ProcurementSettingsService;
import com.pmplatform.procurement.requisition.Requisition;
import com.pmplatform.procurement.requisition.RequisitionRepository;
import com.pmplatform.procurement.supplier.Supplier;
import com.pmplatform.procurement.supplier.SupplierService;
import com.pmplatform.procurement.tender.Bid;
import com.pmplatform.procurement.tender.BidRepository;
import com.pmplatform.procurement.tender.StoredFile;
import com.pmplatform.procurement.tender.Tender;
import com.pmplatform.procurement.tender.TenderFiles;
import com.pmplatform.procurement.tender.TenderRepository;
import com.pmplatform.procurement.tender.TenderStatus;
import com.pmplatform.procurement.tender.api.TenderDtos.Ref;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
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
import org.springframework.web.multipart.MultipartFile;

/**
 * Contracts from award to completion: drafting, signing after the standstill period,
 * securities, the payment schedule, invoices and payment certificates, variations within
 * the legal cap, completion with a supplier rating, and termination. Every change is in the
 * contract's audit history.
 *
 * <p>Who does what: procurement staff prepare and sign contracts and record securities; the
 * assigned contract manager accepts milestones and certifies invoices; finance officers
 * approve and pay certificates (never one they certified); variations follow the
 * CONTRACT_VARIATION approval chain; the accounting officer terminates.
 */
@Service
public class ContractService {

    /** A security expiring within this many days is flagged and notified. */
    public static final int SECURITY_WARNING_DAYS = 30;
    static final ZoneId LOCAL_ZONE = ZoneId.of("Africa/Nairobi");

    /** A file returned for download. */
    public record Download(String fileName, String contentType, long sizeBytes, InputStream content) {
    }

    private final ContractRepository contracts;
    private final MilestoneRepository milestones;
    private final SecurityRepository securities;
    private final InvoiceRepository invoices;
    private final VariationRepository variations;
    private final VariationApprovalRepository steps;
    private final ContractDocumentRepository documents;
    private final ContractNumbers numbers;
    private final TenderRepository tenders;
    private final BidRepository bids;
    private final RequisitionRepository requisitions;
    private final SupplierService suppliers;
    private final ProjectService projects;
    private final ProcurementSettingsService settings;
    private final TenderFiles files;
    private final CurrentUserService currentUser;
    private final NotificationService notifications;
    private final AuditService audit;
    private final Clock clock;
    private final List<RetentionReleaseCheck> retentionChecks;

    public ContractService(ContractRepository contracts, MilestoneRepository milestones, SecurityRepository securities,
            InvoiceRepository invoices, VariationRepository variations, VariationApprovalRepository steps,
            ContractDocumentRepository documents, ContractNumbers numbers, TenderRepository tenders,
            BidRepository bids, RequisitionRepository requisitions, SupplierService suppliers, ProjectService projects,
            ProcurementSettingsService settings, TenderFiles files, CurrentUserService currentUser,
            NotificationService notifications, AuditService audit, Clock clock,
            List<RetentionReleaseCheck> retentionChecks) {
        this.retentionChecks = retentionChecks;
        this.contracts = contracts;
        this.milestones = milestones;
        this.securities = securities;
        this.invoices = invoices;
        this.variations = variations;
        this.steps = steps;
        this.documents = documents;
        this.numbers = numbers;
        this.tenders = tenders;
        this.bids = bids;
        this.requisitions = requisitions;
        this.suppliers = suppliers;
        this.projects = projects;
        this.settings = settings;
        this.files = files;
        this.currentUser = currentUser;
        this.notifications = notifications;
        this.audit = audit;
        this.clock = clock;
    }

    // ================================================================== queries

    @Transactional(readOnly = true)
    public List<ContractSummary> list(UUID tenantId, UUID projectId, UUID tenderId) {
        List<Contract> rows = tenderId != null ? contracts.findByTenantIdAndTenderId(tenantId, tenderId)
                : projectId != null ? contracts.findByTenantIdAndProjectIdOrderByCreatedAtDesc(tenantId, projectId)
                : contracts.findByTenantIdOrderByCreatedAtDesc(tenantId);
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<Invoice>> inv = invoices.findByContractIdIn(rows.stream().map(Contract::getId).toList())
                .stream().collect(Collectors.groupingBy(Invoice::getContractId));
        Map<UUID, Supplier> sup = suppliers.byIds(tenantId, rows.stream().map(Contract::getSupplierId).collect(Collectors.toSet()));
        Map<UUID, Project> proj = projects.byIds(tenantId, rows.stream().map(Contract::getProjectId).collect(Collectors.toSet()));
        Map<UUID, Tender> tend = tenders.findAllById(rows.stream().map(Contract::getTenderId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Tender::getId, Function.identity()));
        Map<UUID, User> people = people(tenantId, rows.stream().map(Contract::getContractManagerId)
                .filter(Objects::nonNull).toList());
        return rows.stream().map(c -> {
            ContractRules.Totals t = ContractRules.totals(inv.getOrDefault(c.getId(), List.of()));
            Supplier s = sup.get(c.getSupplierId());
            Project p = proj.get(c.getProjectId());
            Tender td = tend.get(c.getTenderId());
            return new ContractSummary(c.getId(), c.getNumber(), c.getTitle(), c.getStatus(),
                    new Ref(c.getSupplierId(), s == null ? "Unknown supplier" : s.getName()), c.getProjectId(),
                    p == null ? null : p.getCode(), td == null ? null : td.getNumber(), c.getCurrency(),
                    c.getOriginalValueMinor(), c.getCurrentValueMinor(), t.certified(), t.paid(), c.getEndDate(),
                    ref(people, c.getContractManagerId()), unreleasedRetention(inv.getOrDefault(c.getId(), List.of())),
                    c.defectsLiabilityEnds());
        }).toList();
    }

    @Transactional(readOnly = true)
    public ContractDetail get(UUID tenantId, UUID id) {
        return detail(load(tenantId, id), currentUser.require());
    }

    /** Past performance per supplier, from completed contracts' ratings. */
    @Transactional(readOnly = true)
    public List<SupplierPerformance> supplierPerformance(UUID tenantId) {
        Map<UUID, List<Contract>> bySupplier = contracts.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .filter(c -> c.getStatus() != ContractStatus.CANCELLED && c.getStatus() != ContractStatus.DRAFT)
                .collect(Collectors.groupingBy(Contract::getSupplierId));
        return bySupplier.entrySet().stream().map(e -> {
            List<Contract> list = e.getValue();
            List<Contract> rated = list.stream().filter(c -> c.getRatingQuality() != null).toList();
            Double avg = rated.isEmpty() ? null : rated.stream()
                    .mapToDouble(c -> (c.getRatingQuality() + c.getRatingTimeliness() + c.getRatingCompliance()) / 3.0)
                    .average().orElse(0);
            return new SupplierPerformance(e.getKey(), list.size(),
                    (int) list.stream().filter(c -> c.getStatus() == ContractStatus.COMPLETED).count(),
                    avg == null ? null : Math.round(avg * 10) / 10.0,
                    list.stream().mapToLong(Contract::getCurrentValueMinor).sum());
        }).toList();
    }

    @Transactional(readOnly = true)
    public Download document(UUID tenantId, UUID id, UUID documentId) {
        Contract c = load(tenantId, id);
        ContractDocument d = documents.findByContractIdAndId(c.getId(), documentId)
                .orElseThrow(() -> new NotFoundException("Document not found"));
        return new Download(d.getFileName(), d.getContentType(), d.getSizeBytes(), files.openPlain(d.getStorageKey()));
    }

    // ================================================================== drafting (procurement)

    @Transactional
    public ContractDetail create(UUID tenantId, UUID tenderId, String title) {
        User me = requireProcurement("prepare contracts");
        Tender t = tenders.lockByTenantIdAndId(tenantId, tenderId)
                .orElseThrow(() -> new NotFoundException("Tender " + tenderId + " not found"));
        if (t.getStatus() != TenderStatus.AWARDED) {
            throw new BusinessRuleException("A contract is made from an awarded tender; " + t.getNumber() + " is "
                    + t.getStatus().name().toLowerCase());
        }
        List<Contract> earlier = contracts.findByTenantIdAndTenderId(tenantId, t.getId());
        if (earlier.stream().anyMatch(x -> x.getStatus() != ContractStatus.CANCELLED)) {
            throw new BusinessRuleException(t.getNumber() + " already has a contract");
        }
        if (earlier.stream().anyMatch(x -> x.getReviewStatus() == ReviewStatus.UPHELD)) {
            throw new BusinessRuleException("The Review Board upheld a request for review against this award; "
                    + "the award must be redone (re-evaluation or a new tender) before a contract is made");
        }
        Bid bid = bids.findByTenantIdAndTenderIdAndId(tenantId, t.getId(), t.getAwardedBidId())
                .orElseThrow(() -> new NotFoundException("Awarded bid not found"));
        Instant now = now();
        Contract c = new Contract(tenantId, numbers.next(tenantId), t.getId(), bid.getId(), bid.getSupplierId(),
                t.getProjectId(), t.getRequisitionId(), blankOr(title, t.getTitle()), t.getCurrency(),
                t.getAwardedAmountMinor(), t.getStandstillEndsAt(), me.getId(), now);
        contracts.save(c);
        audit.record(tenantId, "contract.created", "Contract", c.getId(),
                JsonText.object("number", c.getNumber(), "tender", t.getNumber(), "valueMinor",
                        c.getOriginalValueMinor(), "supplierId", c.getSupplierId()));
        return detail(c, me);
    }

    @Transactional
    public ContractDetail edit(UUID tenantId, UUID id, EditContractRequest req) {
        User me = requireProcurement("edit contracts");
        Contract c = lock(tenantId, id);
        if (req.contractManagerId() != null) {
            activeUser(tenantId, req.contractManagerId());
        }
        c.editDraft(req.title().trim(), req.startDate(), req.endDate(),
                req.retentionBp() == null ? c.getRetentionBp() : req.retentionBp(),
                req.defectsLiabilityDays() == null ? c.getDefectsLiabilityDays() : req.defectsLiabilityDays(),
                req.performanceSecurityMinor() == null ? c.getPerformanceSecurityMinor() : req.performanceSecurityMinor(),
                req.contractManagerId(), now());
        audit.record(tenantId, "contract.updated", "Contract", c.getId(),
                JsonText.object("number", c.getNumber(), "start", c.getStartDate(), "end", c.getEndDate(),
                        "retentionBp", c.getRetentionBp(), "performanceSecurityMinor", c.getPerformanceSecurityMinor(),
                        "contractManagerId", c.getContractManagerId()));
        return detail(c, me);
    }

    /**
     * Replaces the planned milestones. In a draft the schedule may change freely; once active,
     * accepted milestones stay, removed planned ones are cancelled (not deleted) and the
     * schedule may not exceed the contract value.
     */
    @Transactional
    public ContractDetail saveSchedule(UUID tenantId, UUID id, List<MilestoneRequest> rows) {
        User me = currentUser.require();
        Contract c = lock(tenantId, id);
        boolean draft = c.getStatus() == ContractStatus.DRAFT;
        if (draft) {
            Roles.requireAny(me, Roles.PROCUREMENT, "change the payment schedule");
        } else if (c.getStatus() == ContractStatus.ACTIVE) {
            requireManagerOrProcurement(c, me, "change the payment schedule");
        } else {
            throw new BusinessRuleException("The payment schedule can only change while the contract is a draft or active");
        }
        List<Milestone> existing = milestones.findByContractIdOrderBySortOrderAsc(c.getId());
        Map<UUID, Milestone> byId = existing.stream().collect(Collectors.toMap(Milestone::getId, Function.identity()));
        Map<UUID, Long> invoicedBy = new HashMap<>();
        invoices.findByContractIdOrderByCertificateNoAsc(c.getId()).stream()
                .filter(i -> i.counts() && i.getMilestoneId() != null)
                .forEach(i -> invoicedBy.merge(i.getMilestoneId(), i.getGrossMinor(), Long::sum));
        Set<UUID> invoiced = invoicedBy.keySet();
        Set<UUID> seen = new HashSet<>();
        for (MilestoneRequest r : rows) {
            if (r.id() != null && !seen.add(r.id())) {
                throw new BusinessRuleException("A milestone appears twice in the schedule");
            }
        }
        Set<UUID> kept = new HashSet<>();
        int order = existing.stream().filter(m -> m.getStatus() != MilestoneStatus.PLANNED)
                .mapToInt(Milestone::getSortOrder).max().orElse(0) + 1;
        List<Milestone> toSave = new ArrayList<>();
        for (MilestoneRequest r : rows) {
            Milestone m = r.id() == null ? null : byId.get(r.id());
            if (r.id() != null && m == null) {
                throw new NotFoundException("Milestone " + r.id() + " not found");
            }
            if (m != null && m.getStatus() != MilestoneStatus.PLANNED) {
                kept.add(m.getId());
                continue; // accepted or cancelled milestones are not edited
            }
            if (m == null) {
                m = new Milestone(c.getId(), order, r.title(), r.description(), r.dueDate(), r.amountMinor());
            } else {
                long already = invoicedBy.getOrDefault(m.getId(), 0L);
                if (r.amountMinor() < already) {
                    throw new BusinessRuleException("Milestone \"" + m.getTitle() + "\" already has " + already
                            + " invoiced (minor units); its value cannot go below that");
                }
                m.edit(order, r.title(), r.description(), r.dueDate(), r.amountMinor());
                kept.add(m.getId());
            }
            order++;
            toSave.add(m);
        }
        for (Milestone m : existing) {
            if (m.getStatus() == MilestoneStatus.PLANNED && !kept.contains(m.getId())) {
                if (invoiced.contains(m.getId())) {
                    throw new BusinessRuleException("Milestone \"" + m.getTitle() + "\" has invoices and cannot be removed");
                }
                if (draft) {
                    milestones.delete(m);
                } else {
                    m.cancel();
                }
            }
        }
        milestones.flush();
        milestones.saveAll(toSave);
        List<Milestone> after = new ArrayList<>(existing.stream()
                .filter(m -> m.getStatus() != MilestoneStatus.PLANNED || kept.contains(m.getId())).toList());
        toSave.stream().filter(m -> !byId.containsKey(m.getId())).forEach(after::add);
        long scheduled = ContractRules.scheduled(after);
        if (!draft && scheduled > c.getCurrentValueMinor()) {
            throw new BusinessRuleException("The milestones add up to " + scheduled + ", above the contract value of "
                    + c.getCurrentValueMinor() + " (minor units); a variation is needed first");
        }
        c.changed(now());
        audit.record(tenantId, "contract.schedule_changed", "Contract", c.getId(),
                JsonText.object("number", c.getNumber(), "milestones", after.stream()
                        .filter(m -> m.getStatus() != MilestoneStatus.CANCELLED).count(), "scheduledMinor", scheduled));
        return detail(c, me);
    }

    @Transactional
    public ContractDetail recordReview(UUID tenantId, UUID id, ReviewStatus status, String note) {
        User me = requireProcurement("record requests for review");
        Contract c = lock(tenantId, id);
        c.recordReview(status, note, now());
        audit.record(tenantId, "contract.review_" + status.name().toLowerCase(), "Contract", c.getId(),
                JsonText.object("number", c.getNumber(), "note", note));
        return detail(c, me);
    }

    @Transactional
    public ContractDetail cancel(UUID tenantId, UUID id, String reason) {
        User me = currentUser.require();
        Roles.requireAny(me, union(Roles.PROCUREMENT, Set.of(Roles.ACCOUNTING_OFFICER)), "cancel draft contracts");
        Contract c = lock(tenantId, id);
        c.cancel(reason, now());
        audit.record(tenantId, "contract.cancelled", "Contract", c.getId(),
                JsonText.object("number", c.getNumber(), "reason", reason));
        return detail(c, me);
    }

    @Transactional
    public ContractDetail sign(UUID tenantId, UUID id, SignRequest req) {
        User me = requireProcurement("record contract signature");
        Contract c = lock(tenantId, id);
        Instant now = now();
        if (req.signedOn().isAfter(today())) {
            throw new BusinessRuleException("The signing date cannot be in the future");
        }
        if (req.signedOn().isBefore(LocalDate.ofInstant(c.getStandstillEndsAt(), LOCAL_ZONE))) {
            throw new BusinessRuleException("The signing date cannot be before the standstill period ended");
        }
        List<String> problems = signingProblems(c, now);
        if (!problems.isEmpty()) {
            throw new BusinessRuleException("The contract cannot be signed yet: " + String.join("; ", problems));
        }
        c.sign(req.signedOn(), req.supplierSignatory(), req.entitySignatory(), me.getId(), now);
        audit.record(tenantId, "contract.signed", "Contract", c.getId(),
                JsonText.object("number", c.getNumber(), "signedOn", req.signedOn(),
                        "supplierSignatory", req.supplierSignatory(), "entitySignatory", req.entitySignatory(),
                        "valueMinor", c.getCurrentValueMinor()));
        notifications.notifyUsers(tenantId, List.of(c.getContractManagerId()), new Message("contract.signed",
                c.getNumber() + " signed: you are the contract manager",
                "\"" + c.getTitle() + "\" runs " + c.getStartDate() + " to " + c.getEndDate()
                        + ". Accept milestones and certify invoices as the work is delivered.", link(c)));
        return detail(c, me);
    }

    // ================================================================== securities and documents

    @Transactional
    public ContractDetail addSecurity(UUID tenantId, UUID id, SecurityKind kind, SecurityForm form, long amountMinor,
            String issuer, String reference, LocalDate expiry, LocalDate received, MultipartFile file) {
        User me = currentUser.require();
        Contract c = lock(tenantId, id);
        requireManagerOrProcurement(c, me, "record securities");
        if (c.getStatus() != ContractStatus.DRAFT && c.getStatus() != ContractStatus.ACTIVE) {
            throw new BusinessRuleException("Securities are recorded for draft or active contracts");
        }
        if (issuer == null || issuer.isBlank() || reference == null || reference.isBlank()) {
            throw new BusinessRuleException("Record the issuer and the security's reference number");
        }
        Security s = securities.save(new Security(c.getId(), kind, form, amountMinor, issuer, reference, expiry,
                received, me.getId()));
        if (file != null && !file.isEmpty()) {
            storeDocument(c, DocumentKind.SECURITY, s.getId(), file, me);
        }
        c.changed(now());
        audit.record(tenantId, "contract.security_received", "Contract", c.getId(),
                JsonText.object("number", c.getNumber(), "kind", kind, "amountMinor", amountMinor, "issuer", issuer,
                        "reference", reference, "expiry", expiry));
        return detail(c, me);
    }

    @Transactional
    public ContractDetail extendSecurity(UUID tenantId, UUID id, UUID securityId, LocalDate newExpiry) {
        User me = currentUser.require();
        Contract c = lock(tenantId, id);
        requireManagerOrProcurement(c, me, "update securities");
        Security s = security(c, securityId);
        LocalDate before = s.getExpiryDate();
        s.extend(newExpiry);
        audit.record(tenantId, "contract.security_extended", "Contract", c.getId(),
                JsonText.object("number", c.getNumber(), "reference", s.getReference(), "from", before, "to", newExpiry));
        return detail(c, me);
    }

    /** Release (return to the supplier) or call (cash) a security. Calling needs the accounting officer. */
    @Transactional
    public ContractDetail closeSecurity(UUID tenantId, UUID id, UUID securityId, SecurityStatus outcome, String note) {
        User me = currentUser.require();
        if (outcome == SecurityStatus.CALLED) {
            Roles.requireAny(me, Set.of(Roles.ACCOUNTING_OFFICER), "call a security");
        } else {
            Roles.requireAny(me, union(Roles.PROCUREMENT, Set.of(Roles.ACCOUNTING_OFFICER)), "release a security");
        }
        Contract c = lock(tenantId, id);
        Security s = security(c, securityId);
        s.close(outcome, note);
        audit.record(tenantId, "contract.security_" + outcome.name().toLowerCase(), "Contract", c.getId(),
                JsonText.object("number", c.getNumber(), "reference", s.getReference(), "note", note));
        return detail(c, me);
    }

    @Transactional
    public ContractDetail uploadDocument(UUID tenantId, UUID id, DocumentKind kind, UUID refId, MultipartFile file) {
        User me = currentUser.require();
        Contract c = lock(tenantId, id);
        if (!mayWork(c, me)) {
            throw new ForbiddenActionException("Only procurement, finance and the contract manager add contract documents");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("Choose a file");
        }
        ContractDocument d = storeDocument(c, kind, refId, file, me);
        c.changed(now());
        audit.record(tenantId, "contract.document_added", "Contract", c.getId(),
                JsonText.object("number", c.getNumber(), "kind", kind, "file", d.getFileName(), "sha256", d.getSha256()));
        return detail(c, me);
    }

    // ================================================================== delivery (contract manager)

    @Transactional
    public ContractDetail acceptMilestone(UUID tenantId, UUID id, UUID milestoneId, AcceptMilestoneRequest req,
            MultipartFile report) {
        User me = currentUser.require();
        Contract c = lock(tenantId, id);
        c.requireStatus(ContractStatus.ACTIVE, "have milestones accepted");
        requireManager(c, me, "accept milestones");
        if (req.acceptedOn().isAfter(today())) {
            throw new BusinessRuleException("The acceptance date cannot be in the future");
        }
        Milestone m = milestones.findById(milestoneId).filter(x -> x.getContractId().equals(c.getId()))
                .orElseThrow(() -> new NotFoundException("Milestone not found"));
        m.accept(req.acceptedOn(), me.getId(), req.note());
        if (report != null && !report.isEmpty()) {
            storeDocument(c, DocumentKind.INSPECTION, m.getId(), report, me);
        }
        c.changed(now());
        audit.record(tenantId, "contract.milestone_accepted", "Contract", c.getId(),
                JsonText.object("number", c.getNumber(), "milestone", m.getTitle(), "amountMinor", m.getAmountMinor(),
                        "acceptedOn", req.acceptedOn(), "note", req.note()));
        return detail(c, me);
    }

    @Transactional
    public ContractDetail recordInvoice(UUID tenantId, UUID id, String supplierRef, LocalDate invoiceDate,
            UUID milestoneId, long grossMinor, MultipartFile scan) {
        User me = currentUser.require();
        Contract c = lock(tenantId, id);
        if (!mayWork(c, me)) {
            throw new ForbiddenActionException("Only procurement, finance and the contract manager record invoices");
        }
        if (!c.isOpenForPayments()) {
            throw new BusinessRuleException("Invoices are recorded against signed contracts");
        }
        if (invoiceDate.isAfter(today())) {
            throw new BusinessRuleException("The invoice date cannot be in the future");
        }
        List<Invoice> existing = invoices.findByContractIdOrderByCertificateNoAsc(c.getId());
        String problem = ContractRules.invoiceCapProblem(c.getCurrentValueMinor(), existing, grossMinor);
        if (problem != null) {
            throw new BusinessRuleException(problem);
        }
        if (milestoneId != null) {
            Milestone m = milestones.findById(milestoneId).filter(x -> x.getContractId().equals(c.getId()))
                    .orElseThrow(() -> new NotFoundException("Milestone not found"));
            if (m.getStatus() == MilestoneStatus.CANCELLED) {
                throw new BusinessRuleException("Milestone \"" + m.getTitle() + "\" was cancelled");
            }
            String mp = ContractRules.milestoneCapProblem(m, existing, grossMinor);
            if (mp != null) {
                throw new BusinessRuleException(mp);
            }
        }
        Instant now = now();
        Invoice inv = invoices.save(new Invoice(c.getId(), InvoiceKind.INVOICE, nextCertificate(existing), supplierRef,
                invoiceDate, milestoneId, grossMinor, ContractRules.retention(grossMinor, c.getRetentionBp()),
                me.getId(), now));
        if (scan != null && !scan.isEmpty()) {
            storeDocument(c, DocumentKind.INVOICE, inv.getId(), scan, me);
        }
        c.changed(now);
        audit.record(tenantId, "contract.invoice_received", "Contract", c.getId(),
                JsonText.object("number", c.getNumber(), "certificate", inv.getCertificateNo(), "supplierRef", supplierRef,
                        "grossMinor", grossMinor, "retentionMinor", inv.getRetentionMinor()));
        if (c.getContractManagerId() != null && !c.getContractManagerId().equals(me.getId())) {
            notifications.notifyUsers(tenantId, List.of(c.getContractManagerId()), new Message("contract.invoice",
                    c.getNumber() + ": invoice to certify", "Payment certificate " + inv.getCertificateNo()
                            + " is waiting for your certification.", link(c)));
        }
        return detail(c, me);
    }

    /** After the defects liability period, the retention held on paid certificates is released to the supplier. */
    @Transactional
    public ContractDetail releaseRetention(UUID tenantId, UUID id) {
        User me = currentUser.require();
        Contract c = lock(tenantId, id);
        requireManager(c, me, "release retention");
        c.requireStatus(ContractStatus.COMPLETED, "release retention");
        if (today().isBefore(c.defectsLiabilityEnds())) {
            throw new BusinessRuleException("Retention is released after the defects liability period ends on "
                    + c.defectsLiabilityEnds());
        }
        List<String> blockers = retentionChecks.stream().flatMap(r -> r.blockers(tenantId, c.getId()).stream()).toList();
        if (!blockers.isEmpty()) {
            throw new BusinessRuleException("Retention cannot be released yet: " + String.join("; ", blockers));
        }
        List<Invoice> existing = invoices.findByContractIdOrderByCertificateNoAsc(c.getId());
        long amount = unreleasedRetention(existing);
        if (amount <= 0) {
            throw new BusinessRuleException("There is no retention left to release");
        }
        Instant now = now();
        Invoice inv = invoices.save(new Invoice(c.getId(), InvoiceKind.RETENTION_RELEASE, nextCertificate(existing),
                null, today(), null, amount, 0, me.getId(), now));
        c.changed(now);
        audit.record(tenantId, "contract.retention_release", "Contract", c.getId(),
                JsonText.object("number", c.getNumber(), "certificate", inv.getCertificateNo(),
                        "amountMinor", inv.getGrossMinor()));
        return detail(c, me);
    }

    @Transactional
    public ContractDetail certify(UUID tenantId, UUID id, UUID invoiceId, String note) {
        User me = currentUser.require();
        Contract c = lock(tenantId, id);
        requireManager(c, me, "certify invoices");
        Invoice inv = invoice(c, invoiceId);
        if (inv.getMilestoneId() != null) {
            Milestone m = milestones.findById(inv.getMilestoneId()).orElseThrow();
            if (m.getStatus() != MilestoneStatus.ACCEPTED) {
                throw new BusinessRuleException("Accept milestone \"" + m.getTitle() + "\" before certifying payment for it");
            }
        }
        inv.certify(me.getId(), note, now());
        c.changed(now());
        audit.record(tenantId, "contract.invoice_certified", "Contract", c.getId(),
                JsonText.object("number", c.getNumber(), "certificate", inv.getCertificateNo(), "netMinor",
                        inv.netMinor(), "note", note));
        notifications.notifyRole(tenantId, Roles.FINANCE_OFFICER, new Message("contract.certified",
                c.getNumber() + ": payment certificate " + inv.getCertificateNo() + " to approve",
                "Certified by " + me.getDisplayName() + ".", link(c)));
        return detail(c, me);
    }

    @Transactional
    public ContractDetail approvePayment(UUID tenantId, UUID id, UUID invoiceId) {
        User me = requireFinance("approve payments");
        Contract c = lock(tenantId, id);
        Invoice inv = invoice(c, invoiceId);
        inv.approve(me.getId(), now());
        c.changed(now());
        audit.record(tenantId, "contract.payment_approved", "Contract", c.getId(),
                JsonText.object("number", c.getNumber(), "certificate", inv.getCertificateNo(), "netMinor", inv.netMinor()));
        return detail(c, me);
    }

    @Transactional
    public ContractDetail pay(UUID tenantId, UUID id, UUID invoiceId, LocalDate paidOn, String ref) {
        User me = requireFinance("record payments");
        Contract c = lock(tenantId, id);
        if (paidOn.isAfter(today())) {
            throw new BusinessRuleException("The payment date cannot be in the future");
        }
        Invoice inv = invoice(c, invoiceId);
        inv.pay(me.getId(), paidOn, ref);
        c.changed(now());
        audit.record(tenantId, "contract.payment_made", "Contract", c.getId(),
                JsonText.object("number", c.getNumber(), "certificate", inv.getCertificateNo(), "netMinor", inv.netMinor(),
                        "paidOn", paidOn, "reference", ref));
        if (c.getContractManagerId() != null) {
            notifications.notifyUsers(tenantId, List.of(c.getContractManagerId()), new Message("contract.paid",
                    c.getNumber() + ": certificate " + inv.getCertificateNo() + " paid", "Reference " + ref + ".", link(c)));
        }
        return detail(c, me);
    }

    @Transactional
    public ContractDetail rejectInvoice(UUID tenantId, UUID id, UUID invoiceId, String reason) {
        User me = currentUser.require();
        Contract c = lock(tenantId, id);
        Invoice inv = invoice(c, invoiceId);
        boolean manager = me.getId().equals(c.getContractManagerId());
        boolean finance = me.hasRole(Roles.FINANCE_OFFICER);
        if (!(manager && inv.getStatus() == InvoiceStatus.RECEIVED) && !finance) {
            throw new ForbiddenActionException("The contract manager rejects invoices before certifying them; "
                    + "after that, finance officers do");
        }
        inv.reject(reason);
        c.changed(now());
        audit.record(tenantId, "contract.invoice_rejected", "Contract", c.getId(),
                JsonText.object("number", c.getNumber(), "certificate", inv.getCertificateNo(), "reason", reason));
        return detail(c, me);
    }

    // ================================================================== variations

    @Transactional
    public ContractDetail requestVariation(UUID tenantId, UUID id, VariationRequest req) {
        User me = currentUser.require();
        Contract c = lock(tenantId, id);
        c.requireStatus(ContractStatus.ACTIVE, "be varied");
        requireManagerOrProcurement(c, me, "request variations");
        List<Variation> existing = variations.findByContractIdOrderByNumberAsc(c.getId());
        if (existing.stream().anyMatch(v -> v.getStatus() == VariationStatus.PENDING)) {
            throw new BusinessRuleException("A variation is already waiting for approval");
        }
        if (req.newEndDate() != null && c.getStartDate() != null && req.newEndDate().isBefore(c.getStartDate())) {
            throw new BusinessRuleException("The new end date is before the start date");
        }
        long change = req.valueChangeMinor() == null ? 0 : req.valueChangeMinor();
        String cap = ContractRules.variationCapProblem(c.getOriginalValueMinor(), approvedIncreases(existing), change);
        if (cap != null) {
            throw new BusinessRuleException(cap);
        }
        long invoicedSoFar = ContractRules.totals(invoices.findByContractIdOrderByCertificateNoAsc(c.getId())).invoiced();
        if (c.getCurrentValueMinor() + change < invoicedSoFar) {
            throw new BusinessRuleException("The contract value cannot go below what has already been invoiced ("
                    + invoicedSoFar + " minor units)");
        }
        Instant now = now();
        Variation v = variations.save(new Variation(c.getId(), existing.size() + 1, change,
                req.newEndDate(), req.reason(), me.getId(), now));
        Tender t = tenders.findById(c.getTenderId()).orElseThrow();
        List<ApprovalStep> chain = settings.check(tenantId, ApprovalStage.CONTRACT_VARIATION, t.getMethodCode(),
                Math.abs(change), c.getCurrency()).approvers();
        List<String> roles = chain.isEmpty() ? List.of(Roles.ACCOUNTING_OFFICER)
                : chain.stream().sorted(Comparator.comparingInt(ApprovalStep::step)).map(ApprovalStep::approverRole).toList();
        for (int i = 0; i < roles.size(); i++) {
            steps.save(new VariationApproval(v.getId(), i + 1, roles.get(i)));
        }
        c.changed(now);
        audit.record(tenantId, "contract.variation_requested", "Contract", c.getId(),
                JsonText.object("number", c.getNumber(), "variation", v.getNumber(), "valueChangeMinor",
                        change, "newEndDate", req.newEndDate(), "reason", req.reason(),
                        "approvers", String.join(", ", roles)));
        notifications.notifyRole(tenantId, roles.getFirst(), new Message("contract.variation",
                c.getNumber() + ": variation " + v.getNumber() + " to approve", req.reason(), link(c)));
        return detail(c, me);
    }

    @Transactional
    public ContractDetail decideVariation(UUID tenantId, UUID id, UUID variationId, boolean approve, String note) {
        User me = currentUser.require();
        Contract c = lock(tenantId, id);
        Variation v = variations.findById(variationId).filter(x -> x.getContractId().equals(c.getId()))
                .orElseThrow(() -> new NotFoundException("Variation not found"));
        if (v.getStatus() != VariationStatus.PENDING) {
            throw new BusinessRuleException("Variation " + v.getNumber() + " was already decided");
        }
        if (v.getRequestedBy().equals(me.getId())) {
            throw new ForbiddenActionException("You cannot approve a variation you requested");
        }
        List<VariationApproval> chain = steps.findByVariationIdInOrderByStepOrderAsc(List.of(v.getId()));
        VariationApproval step = chain.stream().filter(s -> s.getStatus() == StepStatus.PENDING).findFirst()
                .orElseThrow(() -> new BusinessRuleException("No step is waiting"));
        if (!me.hasRole(step.getApproverRole())) {
            throw new ForbiddenActionException("This step needs the " + step.getApproverRole() + " role");
        }
        Instant now = now();
        step.decide(approve, me.getId(), note, now);
        boolean last = chain.getLast().getId().equals(step.getId());
        if (!approve) {
            chain.forEach(VariationApproval::skip);
            v.decide(VariationStatus.REJECTED, now);
        } else if (last) {
            List<Variation> others = variations.findByContractIdOrderByNumberAsc(c.getId()).stream()
                    .filter(x -> !x.getId().equals(v.getId())).toList();
            String cap = ContractRules.variationCapProblem(c.getOriginalValueMinor(), approvedIncreases(others),
                    v.getValueChangeMinor());
            if (cap != null) {
                throw new BusinessRuleException(cap);
            }
            long invoicedSoFar = ContractRules.totals(invoices.findByContractIdOrderByCertificateNoAsc(c.getId())).invoiced();
            if (c.getCurrentValueMinor() + v.getValueChangeMinor() < invoicedSoFar) {
                throw new BusinessRuleException("The contract value cannot go below what has already been invoiced");
            }
            v.decide(VariationStatus.APPROVED, now);
            c.applyVariation(v.getValueChangeMinor(), v.getNewEndDate(), now);
        } else {
            VariationApproval next = chain.get(chain.indexOf(step) + 1);
            notifications.notifyRole(tenantId, next.getApproverRole(), new Message("contract.variation",
                    c.getNumber() + ": variation " + v.getNumber() + " to approve", v.getReason(), link(c)));
        }
        c.changed(now);
        audit.record(tenantId, "contract.variation_decision", "Contract", c.getId(),
                JsonText.object("number", c.getNumber(), "variation", v.getNumber(), "step", step.getStepOrder(),
                        "role", step.getApproverRole(), "approve", approve, "note", note,
                        "result", v.getStatus(), "valueMinor", c.getCurrentValueMinor(), "endDate", c.getEndDate()));
        if (v.getStatus() != VariationStatus.PENDING) {
            notifications.notifyUsers(tenantId, List.of(v.getRequestedBy()), new Message("contract.variation_decided",
                    c.getNumber() + ": variation " + v.getNumber() + " " + v.getStatus().name().toLowerCase(),
                    note == null ? "" : note, link(c)));
        }
        return detail(c, me);
    }

    // ================================================================== close

    @Transactional
    public ContractDetail complete(UUID tenantId, UUID id, CompleteRequest req) {
        User me = currentUser.require();
        Contract c = lock(tenantId, id);
        requireManager(c, me, "complete contracts");
        c.requireStatus(ContractStatus.ACTIVE, "be completed");
        if (variations.findByContractIdOrderByNumberAsc(c.getId()).stream()
                .anyMatch(v -> v.getStatus() == VariationStatus.PENDING)) {
            throw new BusinessRuleException("A variation is still waiting for approval");
        }
        if (c.getSignedAt() != null && req.completedOn().isBefore(c.getSignedAt())) {
            throw new BusinessRuleException("The completion date cannot be before the contract was signed");
        }
        List<String> open = milestones.findByContractIdOrderBySortOrderAsc(c.getId()).stream()
                .filter(m -> m.getStatus() == MilestoneStatus.PLANNED).map(Milestone::getTitle).toList();
        if (!open.isEmpty()) {
            throw new BusinessRuleException("Accept or cancel (by variation) the remaining milestones first: "
                    + String.join(", ", open));
        }
        if (req.completedOn().isAfter(today())) {
            throw new BusinessRuleException("The completion date cannot be in the future");
        }
        c.complete(req.completedOn(), req.quality(), req.timeliness(), req.compliance(), req.comment(), me.getId(), now());
        audit.record(tenantId, "contract.completed", "Contract", c.getId(),
                JsonText.object("number", c.getNumber(), "completedOn", req.completedOn(), "quality", req.quality(),
                        "timeliness", req.timeliness(), "compliance", req.compliance(), "comment", req.comment()));
        return detail(c, me);
    }

    @Transactional
    public ContractDetail terminate(UUID tenantId, UUID id, String reason) {
        User me = currentUser.require();
        Roles.requireAny(me, Set.of(Roles.ACCOUNTING_OFFICER), "terminate contracts");
        Contract c = lock(tenantId, id);
        c.terminate(reason, now());
        audit.record(tenantId, "contract.terminated", "Contract", c.getId(),
                JsonText.object("number", c.getNumber(), "reason", reason));
        Set<UUID> told = new LinkedHashSet<>();
        if (c.getContractManagerId() != null) {
            told.add(c.getContractManagerId());
        }
        notifications.notifyUsers(tenantId, told, new Message("contract.terminated", c.getNumber() + " terminated",
                reason, link(c)));
        return detail(c, me);
    }

    /** Daily, per tenant: notify the contract manager and procurement of securities about to expire. */
    @Transactional
    public int notifyExpiringSecurities(UUID tenantId) {
        LocalDate limit = today().plusDays(SECURITY_WARNING_DAYS);
        List<Security> due = securities.findExpiringUnnotified(tenantId, SecurityStatus.HELD, limit);
        Instant now = now();
        for (Security s : due) {
            Contract c = contracts.findByTenantIdAndId(tenantId, s.getContractId()).orElse(null);
            if (c == null || (c.getStatus() != ContractStatus.ACTIVE && c.getStatus() != ContractStatus.DRAFT)) {
                s.markExpiryNotified(now); // nothing to chase on a closed contract
                continue;
            }
            Message m = new Message("contract.security_expiring", c.getNumber() + ": security expires " + s.getExpiryDate(),
                    s.getKind().name().toLowerCase().replace('_', ' ') + " security " + s.getReference() + " from "
                            + s.getIssuer() + ". Ask the supplier to extend it, or call it before it expires.", link(c));
            if (c.getContractManagerId() != null) {
                notifications.notifyUsers(c.getTenantId(), List.of(c.getContractManagerId()), m);
            }
            notifications.notifyRole(c.getTenantId(), Roles.PROCUREMENT_OFFICER, m);
            s.markExpiryNotified(now);
        }
        return due.size();
    }

    // ================================================================== helpers

    private List<String> signingProblems(Contract c, Instant now) {
        boolean signedDoc = documents.findByContractIdOrderByUploadedAtAsc(c.getId()).stream()
                .anyMatch(d -> d.getKind() == DocumentKind.SIGNED_CONTRACT);
        return ContractRules.signingProblems(c, milestones.findByContractIdOrderBySortOrderAsc(c.getId()),
                securities.findByContractIdOrderByReceivedAtAsc(c.getId()), signedDoc, now);
    }

    /** Retention on paid certificates not yet covered by a (non-rejected) release. */
    private static long unreleasedRetention(Collection<Invoice> list) {
        long retained = list.stream().filter(i -> i.getKind() == InvoiceKind.INVOICE && i.getStatus() == InvoiceStatus.PAID)
                .mapToLong(Invoice::getRetentionMinor).sum();
        long released = list.stream().filter(i -> i.getKind() == InvoiceKind.RETENTION_RELEASE && i.counts())
                .mapToLong(Invoice::getGrossMinor).sum();
        return retained - released;
    }

    private static long approvedIncreases(Collection<Variation> list) {
        return list.stream().filter(v -> v.getStatus() == VariationStatus.APPROVED && v.getValueChangeMinor() > 0)
                .mapToLong(Variation::getValueChangeMinor).sum();
    }

    private static int nextCertificate(List<Invoice> existing) {
        return existing.stream().mapToInt(Invoice::getCertificateNo).max().orElse(0) + 1;
    }

    private ContractDocument storeDocument(Contract c, DocumentKind kind, UUID refId, MultipartFile file, User me) {
        String key = "contracts/" + c.getTenantId() + "/" + c.getId() + "/" + kind.name().toLowerCase() + "/"
                + UUID.randomUUID();
        StoredFile stored = files.storePlain(key, file);
        try {
            return documents.save(new ContractDocument(c.getId(), kind, refId, stored, me.getId(), now()));
        } catch (RuntimeException e) {
            files.deleteQuietly(key);
            throw e;
        }
    }

    private Security security(Contract c, UUID securityId) {
        return securities.findById(securityId).filter(s -> s.getContractId().equals(c.getId()))
                .orElseThrow(() -> new NotFoundException("Security not found"));
    }

    private Invoice invoice(Contract c, UUID invoiceId) {
        return invoices.findById(invoiceId).filter(i -> i.getContractId().equals(c.getId()))
                .orElseThrow(() -> new NotFoundException("Invoice not found"));
    }

    private Contract load(UUID tenantId, UUID id) {
        return contracts.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new NotFoundException("Contract " + id + " not found"));
    }

    private Contract lock(UUID tenantId, UUID id) {
        return contracts.lockByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new NotFoundException("Contract " + id + " not found"));
    }

    private User requireProcurement(String action) {
        User me = currentUser.require();
        Roles.requireAny(me, Roles.PROCUREMENT, action);
        return me;
    }

    private User requireFinance(String action) {
        User me = currentUser.require();
        Roles.requireAny(me, Set.of(Roles.FINANCE_OFFICER), action);
        return me;
    }

    private static void requireManager(Contract c, User me, String action) {
        if (!me.getId().equals(c.getContractManagerId())) {
            throw new ForbiddenActionException("Only the contract's assigned contract manager can " + action);
        }
    }

    private static void requireManagerOrProcurement(Contract c, User me, String action) {
        if (!me.getId().equals(c.getContractManagerId()) && !isProcurement(me)) {
            throw new ForbiddenActionException("Only procurement staff or the contract manager can " + action);
        }
    }

    /** Procurement, finance or the contract's manager. */
    private static boolean mayWork(Contract c, User me) {
        return isProcurement(me) || me.hasRole(Roles.FINANCE_OFFICER) || me.getId().equals(c.getContractManagerId());
    }

    private static boolean isProcurement(User me) {
        return me.getRoles().stream().anyMatch(Roles.PROCUREMENT::contains);
    }

    private static Set<String> union(Set<String> a, Set<String> b) {
        Set<String> out = new HashSet<>(a);
        out.addAll(b);
        return out;
    }

    private void activeUser(UUID tenantId, UUID id) {
        User u = people(tenantId, List.of(id)).get(id);
        if (u == null || !u.isActive()) {
            throw new BusinessRuleException("The contract manager must be an active user of this organisation");
        }
    }

    private Map<UUID, User> people(UUID tenantId, Collection<UUID> ids) {
        return ids.isEmpty() ? Map.of() : currentUser.byIds(tenantId, new HashSet<>(ids)).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private static Ref ref(Map<UUID, User> people, UUID id) {
        if (id == null) {
            return null;
        }
        User u = people.get(id);
        return new Ref(id, u == null ? "Unknown user" : u.getDisplayName());
    }

    private static String link(Contract c) {
        return "/contracts/" + c.getId();
    }

    private static String blankOr(String s, String fallback) {
        return s == null || s.isBlank() ? fallback : s.trim();
    }

    private Instant now() {
        return Instant.now(clock);
    }

    private LocalDate today() {
        return LocalDate.ofInstant(now(), LOCAL_ZONE);
    }

    // ================================================================== view

    private ContractDetail detail(Contract c, User me) {
        UUID tenantId = c.getTenantId();
        Instant now = now();
        LocalDate today = today();
        List<Milestone> ms = milestones.findByContractIdOrderBySortOrderAsc(c.getId());
        List<Security> secs = securities.findByContractIdOrderByReceivedAtAsc(c.getId());
        List<Invoice> invs = invoices.findByContractIdOrderByCertificateNoAsc(c.getId());
        List<Variation> vars = variations.findByContractIdOrderByNumberAsc(c.getId());
        Map<UUID, List<VariationApproval>> stepsByVar = vars.isEmpty() ? Map.of()
                : steps.findByVariationIdInOrderByStepOrderAsc(vars.stream().map(Variation::getId).toList()).stream()
                        .collect(Collectors.groupingBy(VariationApproval::getVariationId));
        List<ContractDocument> docs = documents.findByContractIdOrderByUploadedAtAsc(c.getId());

        Set<UUID> userIds = new HashSet<>();
        userIds.add(c.getCreatedBy());
        Optional.ofNullable(c.getContractManagerId()).ifPresent(userIds::add);
        Optional.ofNullable(c.getRatedBy()).ifPresent(userIds::add);
        ms.forEach(m -> Optional.ofNullable(m.getAcceptedBy()).ifPresent(userIds::add));
        invs.forEach(i -> {
            userIds.add(i.getReceivedBy());
            Optional.ofNullable(i.getCertifiedBy()).ifPresent(userIds::add);
            Optional.ofNullable(i.getApprovedBy()).ifPresent(userIds::add);
            Optional.ofNullable(i.getPaidBy()).ifPresent(userIds::add);
        });
        vars.forEach(v -> userIds.add(v.getRequestedBy()));
        stepsByVar.values().forEach(l -> l.forEach(s -> Optional.ofNullable(s.getDecidedBy()).ifPresent(userIds::add)));
        docs.forEach(d -> userIds.add(d.getUploadedBy()));
        Map<UUID, User> people = people(tenantId, userIds);

        Supplier supplier = suppliers.byIds(tenantId, Set.of(c.getSupplierId())).get(c.getSupplierId());
        Project project = projects.byIds(tenantId, Set.of(c.getProjectId())).get(c.getProjectId());
        Tender tender = tenders.findById(c.getTenderId()).orElse(null);
        Requisition req = requisitions.findById(c.getRequisitionId()).orElse(null);

        boolean procurement = isProcurement(me);
        boolean manager = me.getId().equals(c.getContractManagerId());
        boolean finance = me.hasRole(Roles.FINANCE_OFFICER);
        boolean ao = me.hasRole(Roles.ACCOUNTING_OFFICER);
        ContractStatus st = c.getStatus();

        Map<UUID, Long> invoicedByMilestone = new HashMap<>();
        invs.stream().filter(i -> i.counts() && i.getMilestoneId() != null)
                .forEach(i -> invoicedByMilestone.merge(i.getMilestoneId(), i.getGrossMinor(), Long::sum));
        Map<UUID, String> milestoneTitle = ms.stream().collect(Collectors.toMap(Milestone::getId, Milestone::getTitle));

        List<MilestoneView> milestoneViews = ms.stream().map(m -> new MilestoneView(m.getId(), m.getSortOrder(),
                m.getTitle(), m.getDescription(), m.getDueDate(), m.getAmountMinor(), m.getStatus(), m.getAcceptedAt(),
                ref(people, m.getAcceptedBy()), m.getAcceptanceNote(), invoicedByMilestone.getOrDefault(m.getId(), 0L),
                m.getStatus() == MilestoneStatus.PLANNED && m.getDueDate() != null && m.getDueDate().isBefore(today)))
                .toList();
        List<SecurityView> securityViews = secs.stream().map(s -> new SecurityView(s.getId(), s.getKind(), s.getForm(),
                s.getAmountMinor(), s.getIssuer(), s.getReference(), s.getExpiryDate(), s.getStatus(), s.getReceivedAt(),
                s.getClosedNote(), s.getStatus() == SecurityStatus.HELD
                        && !s.getExpiryDate().isAfter(today.plusDays(SECURITY_WARNING_DAYS)))).toList();
        List<InvoiceView> invoiceViews = invs.stream().map(i -> new InvoiceView(i.getId(), i.getKind(),
                i.getCertificateNo(), i.getSupplierRef(), i.getInvoiceDate(), i.getMilestoneId(),
                i.getMilestoneId() == null ? null : milestoneTitle.get(i.getMilestoneId()), i.getGrossMinor(),
                i.getRetentionMinor(), i.netMinor(), i.getStatus(), ref(people, i.getReceivedBy()), i.getReceivedAt(),
                ref(people, i.getCertifiedBy()), i.getCertifiedAt(), i.getCertificateNote(), ref(people, i.getApprovedBy()),
                i.getApprovedAt(), ref(people, i.getPaidBy()), i.getPaidOn(), i.getPaymentRef(), i.getRejectedReason(),
                manager && i.getStatus() == InvoiceStatus.RECEIVED,
                finance && i.getStatus() == InvoiceStatus.CERTIFIED && !me.getId().equals(i.getCertifiedBy()),
                finance && i.getStatus() == InvoiceStatus.APPROVED,
                (manager && i.getStatus() == InvoiceStatus.RECEIVED)
                        || (finance && i.getStatus() != InvoiceStatus.PAID && i.getStatus() != InvoiceStatus.REJECTED)))
                .toList();
        List<VariationView> variationViews = vars.stream().map(v -> {
            List<VariationApproval> chain = stepsByVar.getOrDefault(v.getId(), List.of());
            Optional<VariationApproval> pending = chain.stream().filter(s -> s.getStatus() == StepStatus.PENDING).findFirst();
            return new VariationView(v.getId(), v.getNumber(), v.getValueChangeMinor(), v.getNewEndDate(), v.getReason(),
                    v.getStatus(), ref(people, v.getRequestedBy()), v.getRequestedAt(), v.getDecidedAt(),
                    chain.stream().map(s -> new StepView(s.getStepOrder(), s.getApproverRole(), s.getStatus(),
                            ref(people, s.getDecidedBy()), s.getDecidedAt(), s.getNote())).toList(),
                    v.getStatus() == VariationStatus.PENDING && pending.filter(s -> me.hasRole(s.getApproverRole())).isPresent()
                            && !v.getRequestedBy().equals(me.getId()));
        }).toList();

        ContractRules.Totals t = ContractRules.totals(invs);
        long scheduled = ContractRules.scheduled(ms);
        long headroom = c.getOriginalValueMinor() * ContractRules.VARIATION_CAP_BP / 10_000 - approvedIncreases(vars);

        boolean signedDoc = docs.stream().anyMatch(d -> d.getKind() == DocumentKind.SIGNED_CONTRACT);
        List<String> signing = st == ContractStatus.DRAFT
                ? ContractRules.signingProblems(c, ms, secs, signedDoc, now) : List.of();
        List<String> warnings = new ArrayList<>();
        if (st == ContractStatus.ACTIVE) {
            if (scheduled != c.getCurrentValueMinor()) {
                warnings.add("The payment schedule adds up to " + scheduled + " but the contract value is "
                        + c.getCurrentValueMinor() + " (minor units). Update the milestones.");
            }
            if (c.getEndDate() != null && c.getEndDate().isBefore(today)) {
                warnings.add("The contract end date (" + c.getEndDate() + ") has passed. Complete the contract or "
                        + "extend it by variation.");
            }
            securityViews.stream().filter(SecurityView::expiringSoon).forEach(s -> warnings.add(
                    "Security " + s.reference() + " expires on " + s.expiryDate() + "."));
            long overdue = milestoneViews.stream().filter(MilestoneView::overdue).count();
            if (overdue > 0) {
                warnings.add(overdue + " milestone(s) are past their due date.");
            }
        }
        if (st == ContractStatus.COMPLETED && t.retentionHeld() > 0 && c.defectsLiabilityEnds() != null
                && !today.isBefore(c.defectsLiabilityEnds())) {
            warnings.add("The defects liability period has ended: retention can be released.");
        }
        if (project != null && project.getCurrency().equals(c.getCurrency())) {
            long committed = contracts.findByTenantIdAndProjectIdOrderByCreatedAtDesc(tenantId, c.getProjectId()).stream()
                    .filter(x -> x.getStatus() == ContractStatus.ACTIVE || x.getStatus() == ContractStatus.COMPLETED
                            || x.getId().equals(c.getId()))
                    .mapToLong(Contract::getCurrentValueMinor).sum();
            if (committed > project.getBudgetMinor()) {
                warnings.add("Contracts on project " + project.getCode() + " now add up to more than its budget.");
            }
        }

        Permissions can = new Permissions(
                procurement && st == ContractStatus.DRAFT,
                procurement && st == ContractStatus.DRAFT,
                (procurement || ao) && st == ContractStatus.DRAFT,
                procurement && st == ContractStatus.DRAFT,
                (procurement || manager) && (st == ContractStatus.DRAFT || st == ContractStatus.ACTIVE),
                procurement || ao,
                procurement || finance || manager,
                manager && st == ContractStatus.ACTIVE,
                (procurement && st == ContractStatus.DRAFT) || ((manager || procurement) && st == ContractStatus.ACTIVE),
                (procurement || finance || manager) && (st == ContractStatus.ACTIVE || st == ContractStatus.COMPLETED),
                manager && st == ContractStatus.COMPLETED && unreleasedRetention(invs) > 0,
                (manager || procurement) && st == ContractStatus.ACTIVE
                        && vars.stream().noneMatch(v -> v.getStatus() == VariationStatus.PENDING),
                manager && st == ContractStatus.ACTIVE,
                ao && st == ContractStatus.ACTIVE);

        Rating rating = c.getRatingQuality() == null ? null : new Rating(c.getRatingQuality(), c.getRatingTimeliness(),
                c.getRatingCompliance(), c.getRatingComment(), ref(people, c.getRatedBy()));

        return new ContractDetail(c.getId(), c.getNumber(), c.getTitle(), st,
                new Ref(c.getSupplierId(), supplier == null ? "Unknown supplier" : supplier.getName()),
                supplier == null ? null : supplier.getKraPin(),
                new Ref(c.getProjectId(), project == null ? "Unknown project" : project.getName()),
                project == null ? null : project.getCode(), c.getTenderId(), tender == null ? null : tender.getNumber(),
                c.getRequisitionId(), req == null ? null : req.getNumber(), c.getCurrency(), c.getOriginalValueMinor(),
                c.getCurrentValueMinor(), c.getRetentionBp(), c.getDefectsLiabilityDays(), c.getPerformanceSecurityMinor(),
                c.getStartDate(), c.getOriginalEndDate(), c.getEndDate(), ref(people, c.getContractManagerId()),
                c.getStandstillEndsAt(), c.getReviewStatus(), c.getReviewNote(), c.getSignedAt(), c.getSupplierSignatory(),
                c.getEntitySignatory(), c.getCompletedAt(), c.defectsLiabilityEnds(), c.getEndedReason(), rating,
                ref(people, c.getCreatedBy()), c.getCreatedAt(), c.getVersion(), milestoneViews, scheduled, securityViews,
                invoiceViews, variationViews,
                docs.stream().map(d -> new DocumentView(d.getId(), d.getKind(), d.getRefId(), d.getFileName(),
                        d.getSizeBytes(), d.getSha256(), ref(people, d.getUploadedBy()), d.getUploadedAt())).toList(),
                new Totals(t.invoiced(), t.certified(), t.paid(), t.retentionHeld(), t.awaitingPayment()),
                Math.max(0, headroom), signing, warnings, can);
    }
}
