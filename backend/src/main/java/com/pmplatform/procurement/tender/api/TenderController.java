package com.pmplatform.procurement.tender.api;

import com.pmplatform.platform.audit.AuditService;
import com.pmplatform.platform.tenant.TenantContext;
import com.pmplatform.portfolio.api.AuditEntryResponse;
import com.pmplatform.procurement.tender.Envelope;
import com.pmplatform.procurement.tender.TenderService;
import com.pmplatform.procurement.tender.TenderService.Download;
import com.pmplatform.procurement.tender.api.TenderDtos.CreateTenderRequest;
import com.pmplatform.procurement.tender.api.TenderDtos.EditTenderRequest;
import com.pmplatform.procurement.tender.api.TenderDtos.ExtendRequest;
import com.pmplatform.procurement.tender.api.TenderDtos.InviteRequest;
import com.pmplatform.procurement.tender.api.TenderDtos.OpeningRequest;
import com.pmplatform.procurement.tender.api.TenderDtos.ReadOutRequest;
import com.pmplatform.procurement.tender.api.TenderDtos.ReasonRequest;
import com.pmplatform.procurement.tender.api.TenderDtos.TenderDetail;
import com.pmplatform.procurement.tender.api.TenderDtos.TenderSummary;
import jakarta.validation.Valid;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/tenders")
public class TenderController {

    private final TenderService service;
    private final AuditService audit;

    public TenderController(TenderService service, AuditService audit) {
        this.service = service;
        this.audit = audit;
    }

    @GetMapping
    public List<TenderSummary> list(@RequestParam(required = false) UUID requisitionId) {
        UUID tenant = TenantContext.require();
        return requisitionId == null ? service.list(tenant) : service.forRequisition(tenant, requisitionId);
    }

    @GetMapping("/{id}")
    public TenderDetail get(@PathVariable UUID id) {
        return service.get(TenantContext.require(), id);
    }

    @PostMapping
    public ResponseEntity<TenderDetail> create(@Valid @RequestBody CreateTenderRequest body) {
        TenderDetail created = service.create(TenantContext.require(), body);
        return ResponseEntity.created(URI.create("/api/v1/tenders/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public TenderDetail edit(@PathVariable UUID id, @Valid @RequestBody EditTenderRequest body) {
        return service.edit(TenantContext.require(), id, body);
    }

    @PostMapping("/{id}/invitations")
    public TenderDetail invite(@PathVariable UUID id, @Valid @RequestBody InviteRequest body) {
        return service.invite(TenantContext.require(), id, body.supplierIds());
    }

    @DeleteMapping("/{id}/invitations/{supplierId}")
    public TenderDetail uninvite(@PathVariable UUID id, @PathVariable UUID supplierId) {
        return service.uninvite(TenantContext.require(), id, supplierId);
    }

    @PostMapping(path = "/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public TenderDetail addDocument(@PathVariable UUID id, @RequestPart("file") MultipartFile file) {
        return service.addDocument(TenantContext.require(), id, file);
    }

    @GetMapping("/{id}/documents/{documentId}")
    public ResponseEntity<InputStreamResource> document(@PathVariable UUID id, @PathVariable UUID documentId) {
        return download(service.document(TenantContext.require(), id, documentId));
    }

    @PostMapping("/{id}/publish")
    public TenderDetail publish(@PathVariable UUID id) {
        return service.publish(TenantContext.require(), id);
    }

    @PostMapping("/{id}/extend")
    public TenderDetail extend(@PathVariable UUID id, @Valid @RequestBody ExtendRequest body) {
        return service.extend(TenantContext.require(), id, body.closingAt(), body.reason());
    }

    @PostMapping("/{id}/cancel")
    public TenderDetail cancel(@PathVariable UUID id, @Valid @RequestBody ReasonRequest body) {
        return service.cancel(TenantContext.require(), id, body.reason());
    }

    /**
     * Records a bid. Files go in the part named after their envelope: {@code single}, or
     * {@code technical} and {@code financial}. A paper bid can be recorded with only a
     * {@code deliveryNote}; its scans are added after opening.
     */
    @PostMapping(path = "/{id}/bids", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public TenderDetail recordBid(@PathVariable UUID id,
            @RequestParam UUID supplierId,
            @RequestParam(required = false) String deliveryNote,
            @RequestPart(name = "single", required = false) List<MultipartFile> single,
            @RequestPart(name = "technical", required = false) List<MultipartFile> technical,
            @RequestPart(name = "financial", required = false) List<MultipartFile> financial) {
        Map<Envelope, List<MultipartFile>> upload = new EnumMap<>(Envelope.class);
        upload.put(Envelope.SINGLE, single == null ? List.of() : single);
        upload.put(Envelope.TECHNICAL, technical == null ? List.of() : technical);
        upload.put(Envelope.FINANCIAL, financial == null ? List.of() : financial);
        return service.recordBid(TenantContext.require(), id, supplierId, deliveryNote, upload);
    }

    @PostMapping("/{id}/bids/{bidId}/withdraw")
    public TenderDetail withdraw(@PathVariable UUID id, @PathVariable UUID bidId,
            @Valid @RequestBody ReasonRequest body) {
        return service.withdrawBid(TenantContext.require(), id, bidId, body.reason());
    }

    @PostMapping("/{id}/openings")
    public TenderDetail open(@PathVariable UUID id, @Valid @RequestBody OpeningRequest body) {
        return service.open(TenantContext.require(), id, body);
    }

    @PutMapping("/{id}/bids/{bidId}/read-out")
    public TenderDetail readOut(@PathVariable UUID id, @PathVariable UUID bidId,
            @Valid @RequestBody ReadOutRequest body) {
        return service.recordReadOut(TenantContext.require(), id, bidId, body.priceMinor(), body.note());
    }

    @PostMapping(path = "/{id}/bids/{bidId}/scans", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public TenderDetail addScans(@PathVariable UUID id, @PathVariable UUID bidId, @RequestParam Envelope envelope,
            @RequestPart("files") List<MultipartFile> files) {
        return service.addScans(TenantContext.require(), id, bidId, envelope, files);
    }

    @GetMapping("/{id}/bids/{bidId}/files/{fileId}")
    public ResponseEntity<InputStreamResource> bidFile(@PathVariable UUID id, @PathVariable UUID bidId,
            @PathVariable UUID fileId) {
        return download(service.bidFile(TenantContext.require(), id, bidId, fileId));
    }

    @GetMapping("/{id}/history")
    public List<AuditEntryResponse> history(@PathVariable UUID id) {
        UUID tenant = TenantContext.require();
        service.get(tenant, id);
        return audit.history(tenant, id).stream().map(AuditEntryResponse::from).toList();
    }

    private static ResponseEntity<InputStreamResource> download(Download d) {
        MediaType type;
        try {
            type = MediaType.parseMediaType(d.contentType());
        } catch (RuntimeException e) {
            type = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok()
                .contentType(type)
                .contentLength(d.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(d.fileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(new InputStreamResource(d.content())); // closed by Spring after streaming
    }
}
