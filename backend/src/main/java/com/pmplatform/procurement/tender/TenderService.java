package com.pmplatform.procurement.tender;

import com.pmplatform.platform.audit.AuditService;
import com.pmplatform.platform.crypto.SealingKeys;
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
import com.pmplatform.procurement.config.EvaluationCommitteeRule;
import com.pmplatform.procurement.config.EvaluationCommitteeRuleRepository;
import com.pmplatform.procurement.config.ProcurementMethod;
import com.pmplatform.procurement.config.ProcurementMethodRepository;
import com.pmplatform.procurement.config.ProcurementRules;
import com.pmplatform.procurement.config.ProcurementSettingsService;
import com.pmplatform.procurement.config.api.SettingsDtos.CheckResponse;
import com.pmplatform.procurement.evaluation.EvaluationService;
import com.pmplatform.procurement.requisition.Requisition;
import com.pmplatform.procurement.requisition.RequisitionRepository;
import com.pmplatform.procurement.requisition.RequisitionStatus;
import com.pmplatform.procurement.supplier.Supplier;
import com.pmplatform.procurement.supplier.SupplierService;
import com.pmplatform.procurement.tender.api.TenderDtos.BidFileResponse;
import com.pmplatform.procurement.tender.api.TenderDtos.BidResponse;
import com.pmplatform.procurement.tender.api.TenderDtos.CreateTenderRequest;
import com.pmplatform.procurement.tender.api.TenderDtos.DocumentResponse;
import com.pmplatform.procurement.tender.api.TenderDtos.EditTenderRequest;
import com.pmplatform.procurement.tender.api.TenderDtos.EnvelopeSummary;
import com.pmplatform.procurement.tender.api.TenderDtos.OpeningRequest;
import com.pmplatform.procurement.tender.api.TenderDtos.OpeningResponse;
import com.pmplatform.procurement.tender.api.TenderDtos.Permissions;
import com.pmplatform.procurement.tender.api.TenderDtos.Ref;
import com.pmplatform.procurement.tender.api.TenderDtos.TenderDetail;
import com.pmplatform.procurement.tender.api.TenderDtos.TenderSummary;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Tenders and sealed bids. Bids are recorded by procurement staff (pilot): their files
 * are encrypted on receipt with the tender's own key and can only be read once the
 * envelope they belong to has been opened, after the closing time, with the quorum
 * present. Every step is in the audit log.
 */
@Service
public class TenderService {

    /** A file returned for download. */
    public record Download(String fileName, String contentType, long sizeBytes, InputStream content) {
    }

    private final TenderRepository tenders;
    private final BidRepository bids;
    private final BidFileRepository bidFiles;
    private final TenderDocumentRepository documents;
    private final BidOpeningRepository openings;
    private final TenderNumbers numbers;
    private final TenderFiles files;
    private final SealingKeys keys;
    private final RequisitionRepository requisitions;
    private final ProjectService projects;
    private final ProcurementSettingsService settings;
    private final ProcurementMethodRepository methods;
    private final EvaluationCommitteeRuleRepository committees;
    private final SupplierService suppliers;
    private final CurrentUserService currentUser;
    private final NotificationService notifications;
    private final AuditService audit;
    private final EvaluationService evaluation;
    private final Clock clock;

    /** Dates such as AGPO certificate expiry are judged in Kenyan time (single pilot tenant). */
    static final ZoneId LOCAL_ZONE = ZoneId.of("Africa/Nairobi");

    public TenderService(TenderRepository tenders, BidRepository bids, BidFileRepository bidFiles,
            TenderDocumentRepository documents, BidOpeningRepository openings, TenderNumbers numbers,
            TenderFiles files, SealingKeys keys, RequisitionRepository requisitions, ProjectService projects,
            ProcurementSettingsService settings, ProcurementMethodRepository methods,
            EvaluationCommitteeRuleRepository committees, SupplierService suppliers, CurrentUserService currentUser,
            NotificationService notifications, AuditService audit, EvaluationService evaluation, Clock clock) {
        this.tenders = tenders;
        this.bids = bids;
        this.bidFiles = bidFiles;
        this.documents = documents;
        this.openings = openings;
        this.numbers = numbers;
        this.files = files;
        this.keys = keys;
        this.requisitions = requisitions;
        this.projects = projects;
        this.settings = settings;
        this.methods = methods;
        this.committees = committees;
        this.suppliers = suppliers;
        this.currentUser = currentUser;
        this.notifications = notifications;
        this.audit = audit;
        this.evaluation = evaluation;
        this.clock = clock;
    }

    // ================================================================== queries

    @Transactional(readOnly = true)
    public List<TenderSummary> list(UUID tenantId) {
        Instant now = now();
        List<Tender> rows = tenders.findByTenantIdOrderByCreatedAtDesc(tenantId, PageRequest.of(0, 200));
        Map<UUID, Project> projectMap = projects.byIds(tenantId,
                rows.stream().map(Tender::getProjectId).collect(Collectors.toSet()));
        Map<UUID, Requisition> reqMap = requisitions.findAllById(
                rows.stream().map(Tender::getRequisitionId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Requisition::getId, Function.identity()));
        return rows.stream().map(t -> new TenderSummary(t.getId(), t.getNumber(), t.getTitle(), t.getMethodCode(),
                t.getStatus(), t.isClosed(now), t.getClosingAt(), t.getEstimatedValueMinor(), t.getCurrency(),
                projectMap.containsKey(t.getProjectId()) ? projectMap.get(t.getProjectId()).getCode() : null,
                reqMap.containsKey(t.getRequisitionId()) ? reqMap.get(t.getRequisitionId()).getNumber() : null,
                bids.countByTenderId(t.getId()), t.getReservedGroup())).toList();
    }

    @Transactional(readOnly = true)
    public List<TenderSummary> forRequisition(UUID tenantId, UUID requisitionId) {
        Set<UUID> ids = tenders.findByTenantIdAndRequisitionIdOrderByCreatedAtDesc(tenantId, requisitionId).stream()
                .map(Tender::getId).collect(Collectors.toSet());
        return list(tenantId).stream().filter(s -> ids.contains(s.id())).toList();
    }

    @Transactional(readOnly = true)
    public TenderDetail get(UUID tenantId, UUID id) {
        return detail(load(tenantId, id), currentUser.require());
    }

    // ================================================================== preparation

    @Transactional
    public TenderDetail create(UUID tenantId, CreateTenderRequest req) {
        User me = requireProcurement("create tenders");
        Requisition r = requisitions.findByTenantIdAndId(tenantId, req.requisitionId())
                .orElseThrow(() -> new NotFoundException("Requisition " + req.requisitionId() + " not found"));
        if (r.getStatus() != RequisitionStatus.APPROVED) {
            throw new BusinessRuleException("Requisition " + r.getNumber() + " must be approved before tendering");
        }
        if (tenders.existsByTenantIdAndRequisitionIdAndStatusNotIn(tenantId, r.getId(),
                List.of(TenderStatus.CANCELLED, TenderStatus.TERMINATED))) {
            throw new BusinessRuleException("Requisition " + r.getNumber() + " already has a tender");
        }
        CheckResponse check = settings.check(tenantId, ApprovalStage.AWARD, req.methodCode(),
                r.getEstimatedTotalMinor(), r.getCurrency());
        if (!check.methodAllowed()) {
            throw new BusinessRuleException(req.methodCode() + " cannot be used for this value. Allowed: "
                    + String.join(", ", check.allowedMethods()));
        }
        ProcurementMethod method = methods.findByTenantIdAndCode(tenantId, req.methodCode()).orElseThrow();
        EvaluationCommitteeRule committee = ProcurementRules
                .committeeRule(committees.findByTenantId(tenantId), req.methodCode())
                .orElseThrow(() -> new BusinessRuleException("Set up an evaluation committee rule first "
                        + "(Procurement settings > Evaluation committees)"));

        Instant now = now();
        Tender t = new Tender(tenantId, numbers.next(tenantId), r.getId(), r.getProjectId(),
                blankOr(req.title(), r.getTitle()), blank(req.description()), method.getCode(), r.getCurrency(),
                r.getEstimatedTotalMinor(), committee.isTwoEnvelope(), method.getMinBidPeriodDays(),
                committee.getQuorum(), !method.isRequiresPublicAdvert(), keys.newWrappedDataKey(), me.getId(), now);
        if (req.reservedGroup() != null) {
            t.reserveFor(req.reservedGroup(), now);
        }
        tenders.save(t);
        audit.record(tenantId, "tender.created", "Tender", t.getId(),
                JsonText.object("number", t.getNumber(), "requisition", r.getNumber(), "method", t.getMethodCode(),
                        "twoEnvelope", t.isTwoEnvelope(), "openingQuorum", t.getOpeningQuorum(),
                        "reservedFor", t.getReservedGroup()));
        return detail(t, me);
    }

    @Transactional
    public TenderDetail edit(UUID tenantId, UUID id, EditTenderRequest req) {
        User me = requireProcurement("edit tenders");
        Tender t = load(tenantId, id);
        Instant now = now();
        t.edit(req.title().trim(), blank(req.description()), req.closingAt(), now);
        if (req.reservedGroup() != t.getReservedGroup()) {
            t.reserveFor(req.reservedGroup(), now);
            LocalDate today = today();
            List<String> ineligible = suppliers.byIds(tenantId, t.getInvitations().stream()
                    .map(Tender.Invitation::getSupplierId).toList()).values().stream()
                    .filter(sup -> req.reservedGroup() != null && !(sup.agpoValidOn(today)
                            && req.reservedGroup().admits(sup.getAgpoCategory())))
                    .map(Supplier::getName).sorted().toList();
            if (!ineligible.isEmpty()) {
                throw new BusinessRuleException("Remove the invited suppliers who are not eligible for the reservation first: "
                        + String.join(", ", ineligible));
            }
        }
        audit.record(tenantId, "tender.updated", "Tender", t.getId(),
                JsonText.object("number", t.getNumber(), "closingAt", t.getClosingAt(),
                        "reservedFor", t.getReservedGroup()));
        return detail(t, me);
    }

    @Transactional
    public TenderDetail invite(UUID tenantId, UUID id, Set<UUID> supplierIds) {
        User me = requireProcurement("invite suppliers");
        Tender t = load(tenantId, id);
        Map<UUID, Supplier> found = suppliers.byIds(tenantId, supplierIds);
        Instant now = now();
        for (UUID sid : supplierIds) {
            Supplier s = found.get(sid);
            if (s == null) {
                throw new NotFoundException("Supplier " + sid + " not found");
            }
            if (!s.mayBid()) {
                throw new BusinessRuleException(s.getName() + " is " + s.getStatus() + " and cannot be invited");
            }
            t.requireEligible(s, today());
            t.invite(sid, now);
        }
        audit.record(tenantId, "tender.suppliers_invited", "Tender", t.getId(),
                JsonText.object("number", t.getNumber(), "suppliers", supplierIds.stream()
                        .map(sid -> found.get(sid).getKraPin()).sorted().collect(Collectors.joining(","))));
        return detail(t, me);
    }

    @Transactional
    public TenderDetail uninvite(UUID tenantId, UUID id, UUID supplierId) {
        User me = requireProcurement("change invitations");
        Tender t = load(tenantId, id);
        t.uninvite(supplierId, now());
        audit.record(tenantId, "tender.supplier_uninvited", "Tender", t.getId(),
                JsonText.object("number", t.getNumber(), "supplierId", supplierId));
        return detail(t, me);
    }

    @Transactional
    public TenderDetail addDocument(UUID tenantId, UUID id, MultipartFile file) {
        User me = requireProcurement("add tender documents");
        Tender t = load(tenantId, id);
        Instant now = now();
        if (t.getStatus() != TenderStatus.DRAFT && !t.acceptingBids(now)) {
            throw new BusinessRuleException("Documents can only be added before the tender closes");
        }
        TenderFiles.requireNotEmpty(file);
        String key = TenderFiles.newKey(tenantId, t.getId(), "documents");
        StoredFile stored = files.storePlain(key, file);
        TenderDocument doc = documents.save(new TenderDocument(null, t.getId(), stored, me.getId(), now));
        audit.record(tenantId, t.getStatus() == TenderStatus.DRAFT ? "tender.document_added" : "tender.addendum_added",
                "Tender", t.getId(), JsonText.object("number", t.getNumber(), "file", doc.getFileName(),
                        "sha256", doc.getSha256(), "size", doc.getSizeBytes()));
        return detail(t, me);
    }

    @Transactional(readOnly = true)
    public Download document(UUID tenantId, UUID id, UUID documentId) {
        Tender t = load(tenantId, id);
        TenderDocument d = documents.findByTenderIdAndId(t.getId(), documentId)
                .orElseThrow(() -> new NotFoundException("Document not found"));
        return new Download(d.getFileName(), d.getContentType(), d.getSizeBytes(), files.openPlain(d.getStorageKey()));
    }

    @Transactional
    public TenderDetail publish(UUID tenantId, UUID id) {
        User me = requireProcurement("publish tenders");
        Tender t = load(tenantId, id);
        if (documents.findByTenderIdOrderByUploadedAtAsc(t.getId()).isEmpty()) {
            throw new BusinessRuleException("Attach the tender document before publishing");
        }
        t.publish(now());
        audit.record(tenantId, "tender.published", "Tender", t.getId(),
                JsonText.object("number", t.getNumber(), "closingAt", t.getClosingAt(),
                        "invited", t.getInvitations().size()));
        requisitions.findById(t.getRequisitionId()).ifPresent(r -> notifications.notifyUsers(tenantId,
                List.of(r.getRequestedBy()), new Message("tender.published", t.getNumber() + " published",
                        "Your requisition " + r.getNumber() + " is now out to tender. Closing: " + t.getClosingAt(),
                        "/tenders/" + t.getId())));
        return detail(t, me);
    }

    @Transactional
    public TenderDetail extend(UUID tenantId, UUID id, Instant closingAt, String reason) {
        User me = requireProcurement("extend tenders");
        Tender t = lock(tenantId, id);
        Instant before = t.getClosingAt();
        t.extendClosing(closingAt, now());
        audit.record(tenantId, "tender.closing_extended", "Tender", t.getId(),
                JsonText.object("number", t.getNumber(), "from", before, "to", closingAt, "reason", reason));
        return detail(t, me);
    }

    @Transactional
    public TenderDetail cancel(UUID tenantId, UUID id, String reason) {
        User me = requireProcurement("cancel tenders");
        Tender t = lock(tenantId, id);
        t.cancel(reason, now());
        audit.record(tenantId, "tender.cancelled", "Tender", t.getId(),
                JsonText.object("number", t.getNumber(), "reason", reason));
        return detail(t, me);
    }

    // ================================================================== bids

    /**
     * Records a bid received by procurement staff. The files are sealed as they are
     * stored; the bid only counts if it is complete before the closing time.
     */
    @Transactional
    public TenderDetail recordBid(UUID tenantId, UUID id, UUID supplierId, String deliveryNote,
            Map<Envelope, List<MultipartFile>> upload) {
        User me = requireProcurement("record bids");
        Tender t = load(tenantId, id);
        Supplier s = suppliers.get(tenantId, supplierId);
        if (!s.mayBid()) {
            throw new BusinessRuleException(s.getName() + " is " + s.getStatus() + " and cannot bid");
        }
        t.requireAcceptingBidFrom(supplierId, now());
        t.requireEligible(s, today());
        for (Envelope e : upload.keySet()) {
            if (!t.envelopes().contains(e) && !upload.get(e).isEmpty()) {
                throw new BusinessRuleException("This tender uses " + t.envelopes() + " envelopes, not " + e);
            }
        }
        boolean hasFiles = upload.values().stream().anyMatch(l -> !l.isEmpty());
        if (!hasFiles && (deliveryNote == null || deliveryNote.isBlank())) {
            throw new BusinessRuleException("Attach the bid files, or describe the physical bid received "
                    + "(for example \"2 sealed envelopes, hand delivered\")");
        }

        // Seal and store the files first (this can take a while for large files) ...
        List<StoredFile> stored = new ArrayList<>();
        List<Envelope> storedEnvelopes = new ArrayList<>();
        try {
            for (Envelope e : t.envelopes()) {
                for (MultipartFile f : upload.getOrDefault(e, List.of())) {
                    TenderFiles.requireNotEmpty(f);
                    stored.add(files.storeSealed(TenderFiles.newKey(tenantId, t.getId(), "bids"), f, t.getWrappedKey()));
                    storedEnvelopes.add(e);
                }
            }

            // ... then lock the tender and check the deadline against the time the bid is complete.
            Tender locked = lock(tenantId, id);
            Instant receivedAt = now();
            locked.requireAcceptingBidFrom(supplierId, receivedAt);
            if (bids.existsByTenderIdAndSupplierIdAndStatus(locked.getId(), supplierId, BidStatus.RECEIVED)) {
                throw new BusinessRuleException(s.getName() + " already has a bid on this tender; "
                        + "withdraw it first to record a replacement");
            }
            String receiptNo = locked.getNumber() + "-B" + "%03d".formatted(bids.countByTenderId(locked.getId()) + 1);
            List<ReceiptHash.Part> parts = new ArrayList<>();
            for (int i = 0; i < stored.size(); i++) {
                parts.add(new ReceiptHash.Part(storedEnvelopes.get(i), stored.get(i).sizeBytes(), stored.get(i).sha256()));
            }
            Bid bid = new Bid(tenantId, locked.getId(), supplierId, receiptNo, receivedAt, me.getId(),
                    blank(deliveryNote), ReceiptHash.of(locked.getId(), supplierId, receiptNo, receivedAt, parts));
            bids.save(bid);
            for (int i = 0; i < stored.size(); i++) {
                bidFiles.save(new BidFile(null, bid.getId(), storedEnvelopes.get(i), stored.get(i), false, me.getId(),
                        receivedAt));
            }
            audit.record(tenantId, "bid.received", "Tender", locked.getId(),
                    JsonText.object("number", locked.getNumber(), "receiptNo", receiptNo, "supplier", s.getKraPin(),
                            "files", stored.size(), "receiptHash", bid.getReceiptHash()));
            return detail(locked, me);
        } catch (RuntimeException e) {
            stored.forEach(f -> files.deleteQuietly(f.storageKey()));
            throw e;
        }
    }

    @Transactional
    public TenderDetail withdrawBid(UUID tenantId, UUID id, UUID bidId, String reason) {
        User me = requireProcurement("withdraw bids");
        Tender t = lock(tenantId, id);
        Instant now = now();
        if (!t.acceptingBids(now)) {
            throw new BusinessRuleException("Bids can only be withdrawn before the closing time");
        }
        Bid bid = loadBid(tenantId, t, bidId);
        bid.withdraw(reason, now);
        audit.record(tenantId, "bid.withdrawn", "Tender", t.getId(),
                JsonText.object("number", t.getNumber(), "receiptNo", bid.getReceiptNo(), "reason", reason));
        return detail(t, me);
    }

    // ================================================================== opening

    @Transactional
    public TenderDetail open(UUID tenantId, UUID id, OpeningRequest req) {
        User me = requireProcurement("open bids");
        Tender t = lock(tenantId, id);
        Set<UUID> attendees = new HashSet<>(req.attendeeIds());
        List<User> people = currentUser.byIds(tenantId, attendees);
        if (people.size() != attendees.size()) {
            throw new BusinessRuleException("Some attendees are not users of this organisation");
        }
        Instant now = now();
        if (req.envelope() == Envelope.FINANCIAL) {
            evaluation.financialOpeningBlocked(tenantId, t.getId()).ifPresent(reason -> {
                throw new BusinessRuleException(reason);
            });
        }
        t.open(req.envelope(), attendees.size(), now);
        BidOpening opening = openings.save(new BidOpening(tenantId, t.getId(), req.envelope(), now, me.getId(),
                blank(req.venue()), blank(req.notes()), attendees));
        List<Bid> live = bids.findByTenantIdAndTenderIdOrderByReceivedAtAsc(tenantId, t.getId()).stream()
                .filter(b -> b.getStatus() == BidStatus.RECEIVED)
                .filter(b -> req.envelope() != Envelope.FINANCIAL || b.isInContention()).toList();
        audit.record(tenantId, "tender.bids_opened", "Tender", t.getId(),
                JsonText.object("number", t.getNumber(), "envelope", req.envelope(), "bids", live.size(),
                        "receipts", live.stream().map(Bid::getReceiptNo).collect(Collectors.joining(", ")),
                        "attendees", people.stream().map(User::getDisplayName).sorted()
                                .collect(Collectors.joining("; ")),
                        "openingId", opening.getId()));
        return detail(t, me);
    }

    @Transactional
    public TenderDetail recordReadOut(UUID tenantId, UUID id, UUID bidId, Long priceMinor, String note) {
        User me = requireProcurement("record read-out prices");
        Tender t = load(tenantId, id);
        if (!t.pricesOpen()) {
            throw new BusinessRuleException("Prices are recorded once the "
                    + (t.isTwoEnvelope() ? "financial envelopes are" : "bids are") + " opened");
        }
        Bid bid = loadBid(tenantId, t, bidId);
        if (sealedForBid(t, bid, t.isTwoEnvelope() ? Envelope.FINANCIAL : Envelope.SINGLE)) {
            throw new BusinessRuleException("Bid " + bid.getReceiptNo() + " did not pass the "
                    + bid.getDisqualifiedStage().name().toLowerCase() + " evaluation; its financial envelope stays sealed");
        }
        Long before = bid.getReadOutPriceMinor();
        bid.recordReadOut(priceMinor, note, now());
        audit.record(tenantId, "bid.price_read_out", "Tender", t.getId(),
                JsonText.object("number", t.getNumber(), "receiptNo", bid.getReceiptNo(), "priceMinor", priceMinor,
                        "previousPriceMinor", before, "note", note));
        return detail(t, me);
    }

    /** Adds scans of a paper bid to an envelope that is already open. */
    @Transactional
    public TenderDetail addScans(UUID tenantId, UUID id, UUID bidId, Envelope envelope, List<MultipartFile> upload) {
        User me = requireProcurement("add bid scans");
        Tender t = load(tenantId, id);
        if (!t.isOpen(envelope)) {
            throw new BusinessRuleException("Scans can only be added to an envelope after it has been opened");
        }
        Bid bid = loadBid(tenantId, t, bidId);
        if (bid.getStatus() != BidStatus.RECEIVED) {
            throw new BusinessRuleException("The bid was withdrawn");
        }
        if (sealedForBid(t, bid, envelope)) {
            throw new BusinessRuleException("This bid's " + envelope.name().toLowerCase() + " envelope stays sealed");
        }
        Instant now = now();
        List<String> names = new ArrayList<>();
        for (MultipartFile f : upload) {
            TenderFiles.requireNotEmpty(f);
            StoredFile s = files.storeSealed(TenderFiles.newKey(tenantId, t.getId(), "bids"), f, t.getWrappedKey());
            bidFiles.save(new BidFile(null, bid.getId(), envelope, s, true, me.getId(), now));
            names.add(s.fileName() + " " + s.sha256());
        }
        audit.record(tenantId, "bid.scans_added", "Tender", t.getId(),
                JsonText.object("number", t.getNumber(), "receiptNo", bid.getReceiptNo(), "envelope", envelope,
                        "files", String.join("; ", names)));
        return detail(t, me);
    }

    /** Decrypts a bid file for download, only after its envelope is open. Each download is logged. */
    @Transactional
    public Download bidFile(UUID tenantId, UUID id, UUID bidId, UUID fileId) {
        User me = currentUser.require();
        Tender t = load(tenantId, id);
        Bid bid = loadBid(tenantId, t, bidId);
        BidFile f = bidFiles.findByBidIdAndId(bid.getId(), fileId)
                .orElseThrow(() -> new NotFoundException("File not found"));
        if (sealedForBid(t, bid, f.getEnvelope())) {
            throw new BusinessRuleException(t.isOpen(f.getEnvelope())
                    ? "This bid did not pass the technical evaluation; its financial envelope stays sealed"
                    : "This file is sealed until its envelope is opened");
        }
        if (!evaluation.mayReadBids(tenantId, t.getId(), me)) {
            throw new ForbiddenActionException("Bid documents are open to procurement staff and to evaluation "
                    + "committee members who have declared no conflict of interest");
        }
        audit.record(tenantId, "bid.file_downloaded", "Tender", t.getId(),
                JsonText.object("number", t.getNumber(), "receiptNo", bid.getReceiptNo(), "file", f.getFileName(),
                        "by", me.getDisplayName()));
        return new Download(f.getFileName(), f.getContentType(), f.getSizeBytes(),
                files.openSealed(f, t.getWrappedKey()));
    }

    // ================================================================== helpers

    private User requireProcurement(String action) {
        User me = currentUser.require();
        Roles.requireAny(me, Roles.PROCUREMENT, action);
        return me;
    }

    private Tender load(UUID tenantId, UUID id) {
        return tenders.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new NotFoundException("Tender " + id + " not found"));
    }

    private Tender lock(UUID tenantId, UUID id) {
        return tenders.lockByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new NotFoundException("Tender " + id + " not found"));
    }

    private Bid loadBid(UUID tenantId, Tender t, UUID bidId) {
        return bids.findByTenantIdAndTenderIdAndId(tenantId, t.getId(), bidId)
                .orElseThrow(() -> new NotFoundException("Bid " + bidId + " not found on " + t.getNumber()));
    }

    private Instant now() {
        return Instant.now(clock);
    }

    private LocalDate today() {
        return LocalDate.ofInstant(now(), LOCAL_ZONE);
    }

    /**
     * Whether an envelope of this bid is still sealed. In a two-envelope tender the financial
     * envelope of a bid that failed the preliminary or technical evaluation is never opened.
     */
    static boolean sealedForBid(Tender t, Bid b, Envelope e) {
        if (!t.isOpen(e)) {
            return true;
        }
        return e == Envelope.FINANCIAL && b.getDisqualifiedStage() != null
                && b.getDisqualifiedStage() != EvaluationStage.FINANCIAL;
    }

    private TenderDetail detail(Tender t, User me) {
        UUID tenantId = t.getTenantId();
        Instant now = now();
        List<TenderDocument> docs = documents.findByTenderIdOrderByUploadedAtAsc(t.getId());
        List<Bid> bidRows = bids.findByTenantIdAndTenderIdOrderByReceivedAtAsc(tenantId, t.getId());
        Map<UUID, List<BidFile>> filesByBid = bidRows.isEmpty() ? Map.of()
                : bidFiles.findByBidIdInOrderByUploadedAtAsc(bidRows.stream().map(Bid::getId).toList()).stream()
                        .collect(Collectors.groupingBy(BidFile::getBidId));
        List<BidOpening> openingRows = openings.findByTenantIdAndTenderIdOrderByOpenedAtAsc(tenantId, t.getId());

        Set<UUID> supplierIds = new HashSet<>();
        bidRows.forEach(b -> supplierIds.add(b.getSupplierId()));
        t.getInvitations().forEach(i -> supplierIds.add(i.getSupplierId()));
        Map<UUID, Supplier> supplierMap = suppliers.byIds(tenantId, supplierIds);

        Set<UUID> userIds = new HashSet<>();
        userIds.add(t.getCreatedBy());
        docs.forEach(d -> userIds.add(d.getUploadedBy()));
        bidRows.forEach(b -> userIds.add(b.getReceivedBy()));
        openingRows.forEach(o -> {
            userIds.add(o.getOpenedBy());
            userIds.addAll(o.getAttendees());
        });
        Map<UUID, User> people = currentUser.byIds(tenantId, userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        Project project = projects.byIds(tenantId, Set.of(t.getProjectId())).get(t.getProjectId());
        Requisition req = requisitions.findById(t.getRequisitionId()).orElse(null);
        String methodName = methods.findByTenantIdAndCode(tenantId, t.getMethodCode())
                .map(ProcurementMethod::getName).orElse(t.getMethodCode());

        boolean procurement = me.getRoles().stream().anyMatch(Roles.PROCUREMENT::contains);
        boolean draft = t.getStatus() == TenderStatus.DRAFT;
        String openingBlocked = t.nextEnvelopeToOpen() == Envelope.FINANCIAL
                ? evaluation.financialOpeningBlocked(tenantId, t.getId()).orElse(null) : null;
        Permissions can = new Permissions(
                procurement && draft,
                procurement && draft,
                procurement && (draft || t.acceptingBids(now)),
                procurement && t.acceptingBids(now),
                procurement && (draft || t.getStatus() == TenderStatus.PUBLISHED),
                procurement && t.acceptingBids(now),
                procurement && t.nextEnvelopeToOpen() != null && t.getClosingAt() != null
                        && !now.isBefore(t.getClosingAt()) && openingBlocked == null,
                procurement && t.getStatus() == TenderStatus.OPENED,
                procurement && (t.getStatus() == TenderStatus.TECHNICAL_OPENED || t.getStatus() == TenderStatus.OPENED),
                t.anyOpened() && evaluation.mayReadBids(tenantId, t.getId(), me));

        return new TenderDetail(t.getId(), t.getNumber(), t.getTitle(), t.getDescription(), t.getStatus(),
                t.acceptingBids(now), t.isClosed(now), t.getMethodCode(), methodName, t.isTwoEnvelope(),
                t.isInvitedOnly(), t.getMinBidPeriodDays(), t.getOpeningQuorum(), t.earliestClosing(now),
                t.getClosingAt(), t.getPublishedAt(), t.getCancelledReason(), t.getCurrency(),
                t.getEstimatedValueMinor(),
                new Ref(t.getProjectId(), project == null ? "Unknown project" : project.getName()),
                project == null ? null : project.getCode(),
                new Ref(t.getRequisitionId(), req == null ? "Unknown requisition" : req.getTitle()),
                req == null ? null : req.getNumber(),
                ref(people, t.getCreatedBy()), t.getCreatedAt(), t.getVersion(), t.nextEnvelopeToOpen(),
                docs.stream().map(d -> new DocumentResponse(d.getId(), d.getFileName(), d.getContentType(),
                        d.getSizeBytes(), d.getSha256(), ref(people, d.getUploadedBy()), d.getUploadedAt())).toList(),
                t.getInvitations().stream().map(i -> supplierRef(supplierMap, i.getSupplierId())).toList(),
                bidRows.stream().map(b -> bidView(t, b, filesByBid.getOrDefault(b.getId(), List.of()), supplierMap,
                        people)).toList(),
                openingRows.stream().map(o -> new OpeningResponse(o.getId(), o.getEnvelope(), o.getOpenedAt(),
                        ref(people, o.getOpenedBy()), o.getVenue(), o.getNotes(),
                        o.getAttendees().stream().map(a -> ref(people, a))
                                .sorted(java.util.Comparator.comparing(Ref::name)).toList())).toList(),
                now, can, t.getReservedGroup(), evaluation.status(tenantId, t.getId()).orElse(null), openingBlocked,
                t.getAwardedBidId(), t.getAwardedAmountMinor(), t.getAwardedAt(), t.getStandstillEndsAt());
    }

    private static BidResponse bidView(Tender t, Bid b, List<BidFile> fileRows, Map<UUID, Supplier> suppliers,
            Map<UUID, User> people) {
        List<EnvelopeSummary> envelopes = t.envelopes().stream().map(e -> new EnvelopeSummary(e,
                (int) fileRows.stream().filter(f -> f.getEnvelope() == e).count(), sealedForBid(t, b, e))).toList();
        List<BidFileResponse> fileViews = fileRows.stream().map(f -> {
            boolean sealed = sealedForBid(t, b, f.getEnvelope());
            return new BidFileResponse(f.getId(), f.getEnvelope(), sealed, sealed ? null : f.getFileName(),
                    sealed ? null : f.getSizeBytes(), sealed ? null : f.getSha256(), f.isAddedAfterOpening(),
                    f.getUploadedAt());
        }).toList();
        Supplier s = suppliers.get(b.getSupplierId());
        boolean pricesShown = !sealedForBid(t, b, t.isTwoEnvelope() ? Envelope.FINANCIAL : Envelope.SINGLE);
        return new BidResponse(b.getId(), supplierRef(suppliers, b.getSupplierId()), s == null ? null : s.getKraPin(),
                b.getStatus(), b.getReceiptNo(), b.getReceivedAt(), ref(people, b.getReceivedBy()), b.getDeliveryNote(),
                b.getReceiptHash(), b.getWithdrawnAt(), b.getWithdrawReason(), envelopes, fileViews,
                pricesShown ? b.getReadOutPriceMinor() : null, pricesShown ? b.getReadOutNote() : null,
                b.getDisqualifiedStage(), b.getDisqualifiedReason());
    }

    private static Ref ref(Map<UUID, User> people, UUID id) {
        User u = people.get(id);
        return new Ref(id, u == null ? "Unknown user" : u.getDisplayName());
    }

    private static Ref supplierRef(Map<UUID, Supplier> suppliers, UUID id) {
        Supplier s = suppliers.get(id);
        return new Ref(id, s == null ? "Unknown supplier" : s.getName());
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String blankOr(String s, String fallback) {
        return s == null || s.isBlank() ? fallback : s.trim();
    }
}
