package com.pmplatform.contracts.api;

import com.pmplatform.contracts.ContractService;
import com.pmplatform.contracts.ContractService.Download;
import com.pmplatform.contracts.DocumentKind;
import com.pmplatform.contracts.SecurityForm;
import com.pmplatform.contracts.SecurityKind;
import com.pmplatform.contracts.api.ContractDtos.AcceptMilestoneRequest;
import com.pmplatform.contracts.api.ContractDtos.CertifyRequest;
import com.pmplatform.contracts.api.ContractDtos.CloseSecurityRequest;
import com.pmplatform.contracts.api.ContractDtos.CompleteRequest;
import com.pmplatform.contracts.api.ContractDtos.ContractDetail;
import com.pmplatform.contracts.api.ContractDtos.ContractSummary;
import com.pmplatform.contracts.api.ContractDtos.CreateContractRequest;
import com.pmplatform.contracts.api.ContractDtos.EditContractRequest;
import com.pmplatform.contracts.api.ContractDtos.ExtendSecurityRequest;
import com.pmplatform.contracts.api.ContractDtos.PayRequest;
import com.pmplatform.contracts.api.ContractDtos.ReasonRequest;
import com.pmplatform.contracts.api.ContractDtos.ReviewRequest;
import com.pmplatform.contracts.api.ContractDtos.ScheduleRequest;
import com.pmplatform.contracts.api.ContractDtos.SignRequest;
import com.pmplatform.contracts.api.ContractDtos.SupplierPerformance;
import com.pmplatform.contracts.api.ContractDtos.VariationDecisionRequest;
import com.pmplatform.contracts.api.ContractDtos.VariationRequest;
import com.pmplatform.platform.audit.AuditService;
import com.pmplatform.platform.tenant.TenantContext;
import com.pmplatform.platform.web.BusinessRuleException;
import com.pmplatform.portfolio.api.AuditEntryResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

/** Contracts from award to completion. Multipart endpoints accept an optional supporting file. */
@RestController
@RequestMapping("/api/v1/contracts")
public class ContractController {

    private final ContractService service;
    private final AuditService audit;

    public ContractController(ContractService service, AuditService audit) {
        this.service = service;
        this.audit = audit;
    }

    @GetMapping
    public List<ContractSummary> list(@RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) UUID tenderId) {
        return service.list(TenantContext.require(), projectId, tenderId);
    }

    @GetMapping("/supplier-performance")
    public List<SupplierPerformance> supplierPerformance() {
        return service.supplierPerformance(TenantContext.require());
    }

    @GetMapping("/{id}")
    public ContractDetail get(@PathVariable UUID id) {
        return service.get(TenantContext.require(), id);
    }

    @GetMapping("/{id}/history")
    public List<AuditEntryResponse> history(@PathVariable UUID id) {
        UUID tenant = TenantContext.require();
        service.get(tenant, id);
        return audit.history(tenant, id).stream().map(AuditEntryResponse::from).toList();
    }

    // ---- drafting

    @PostMapping
    public ResponseEntity<ContractDetail> create(@Valid @RequestBody CreateContractRequest body) {
        ContractDetail d = service.create(TenantContext.require(), body.tenderId(), body.title());
        return ResponseEntity.created(URI.create("/api/v1/contracts/" + d.id())).body(d);
    }

    @PutMapping("/{id}")
    public ContractDetail edit(@PathVariable UUID id, @Valid @RequestBody EditContractRequest body) {
        return service.edit(TenantContext.require(), id, body);
    }

    @PutMapping("/{id}/milestones")
    public ContractDetail schedule(@PathVariable UUID id, @Valid @RequestBody ScheduleRequest body) {
        return service.saveSchedule(TenantContext.require(), id, body.milestones());
    }

    @PostMapping("/{id}/review")
    public ContractDetail review(@PathVariable UUID id, @Valid @RequestBody ReviewRequest body) {
        return service.recordReview(TenantContext.require(), id, body.status(), body.note());
    }

    @PostMapping("/{id}/cancel")
    public ContractDetail cancel(@PathVariable UUID id, @Valid @RequestBody ReasonRequest body) {
        return service.cancel(TenantContext.require(), id, body.reason());
    }

    @PostMapping("/{id}/sign")
    public ContractDetail sign(@PathVariable UUID id, @Valid @RequestBody SignRequest body) {
        return service.sign(TenantContext.require(), id, body);
    }

    // ---- securities and documents

    @PostMapping(path = "/{id}/securities", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ContractDetail addSecurity(@PathVariable UUID id, @RequestParam SecurityKind kind,
            @RequestParam SecurityForm form, @RequestParam long amountMinor, @RequestParam String issuer,
            @RequestParam String reference,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiryDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate receivedOn,
            @RequestPart(name = "file", required = false) MultipartFile file) {
        if (issuer.length() > 200 || reference.length() > 100) {
            throw new BusinessRuleException("The issuer is at most 200 characters and the reference at most 100");
        }
        return service.addSecurity(TenantContext.require(), id, kind, form, amountMinor, issuer, reference, expiryDate,
                receivedOn, file);
    }

    @PostMapping("/{id}/securities/{securityId}/extend")
    public ContractDetail extendSecurity(@PathVariable UUID id, @PathVariable UUID securityId,
            @Valid @RequestBody ExtendSecurityRequest body) {
        return service.extendSecurity(TenantContext.require(), id, securityId, body.expiryDate());
    }

    @PostMapping("/{id}/securities/{securityId}/close")
    public ContractDetail closeSecurity(@PathVariable UUID id, @PathVariable UUID securityId,
            @Valid @RequestBody CloseSecurityRequest body) {
        return service.closeSecurity(TenantContext.require(), id, securityId, body.outcome(), body.note());
    }

    @PostMapping(path = "/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ContractDetail upload(@PathVariable UUID id, @RequestParam DocumentKind kind,
            @RequestParam(required = false) UUID refId, @RequestPart("file") MultipartFile file) {
        return service.uploadDocument(TenantContext.require(), id, kind, refId, file);
    }

    @GetMapping("/{id}/documents/{documentId}")
    public ResponseEntity<InputStreamResource> document(@PathVariable UUID id, @PathVariable UUID documentId) {
        Download d = service.document(TenantContext.require(), id, documentId);
        MediaType type;
        try {
            type = MediaType.parseMediaType(d.contentType());
        } catch (RuntimeException e) {
            type = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok().contentType(type).contentLength(d.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(d.fileName(), java.nio.charset.StandardCharsets.UTF_8)
                                .build().toString())
                .body(new InputStreamResource(d.content()));
    }

    // ---- delivery

    @PostMapping(path = "/{id}/milestones/{milestoneId}/accept", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ContractDetail accept(@PathVariable UUID id, @PathVariable UUID milestoneId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate acceptedOn,
            @RequestParam String note, @RequestPart(name = "file", required = false) MultipartFile report) {
        if (note == null || note.isBlank() || note.length() > 2000) {
            throw new BusinessRuleException("Record the inspection or acceptance details (up to 2000 characters)");
        }
        return service.acceptMilestone(TenantContext.require(), id, milestoneId,
                new AcceptMilestoneRequest(acceptedOn, note), report);
    }

    @PostMapping(path = "/{id}/invoices", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ContractDetail invoice(@PathVariable UUID id, @RequestParam(required = false) String supplierRef,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate invoiceDate,
            @RequestParam(required = false) UUID milestoneId, @RequestParam long grossMinor,
            @RequestPart(name = "file", required = false) MultipartFile scan) {
        if (supplierRef != null && supplierRef.length() > 100) {
            throw new BusinessRuleException("The supplier's invoice number is at most 100 characters");
        }
        return service.recordInvoice(TenantContext.require(), id, supplierRef, invoiceDate, milestoneId, grossMinor, scan);
    }

    @PostMapping("/{id}/retention-release")
    public ContractDetail releaseRetention(@PathVariable UUID id) {
        return service.releaseRetention(TenantContext.require(), id);
    }

    @PostMapping("/{id}/invoices/{invoiceId}/certify")
    public ContractDetail certify(@PathVariable UUID id, @PathVariable UUID invoiceId,
            @Valid @RequestBody CertifyRequest body) {
        return service.certify(TenantContext.require(), id, invoiceId, body.note());
    }

    @PostMapping("/{id}/invoices/{invoiceId}/approve")
    public ContractDetail approve(@PathVariable UUID id, @PathVariable UUID invoiceId) {
        return service.approvePayment(TenantContext.require(), id, invoiceId);
    }

    @PostMapping("/{id}/invoices/{invoiceId}/pay")
    public ContractDetail pay(@PathVariable UUID id, @PathVariable UUID invoiceId, @Valid @RequestBody PayRequest body) {
        return service.pay(TenantContext.require(), id, invoiceId, body.paidOn(), body.paymentRef());
    }

    @PostMapping("/{id}/invoices/{invoiceId}/reject")
    public ContractDetail reject(@PathVariable UUID id, @PathVariable UUID invoiceId,
            @Valid @RequestBody ReasonRequest body) {
        return service.rejectInvoice(TenantContext.require(), id, invoiceId, body.reason());
    }

    // ---- variations and close

    @PostMapping("/{id}/variations")
    public ContractDetail requestVariation(@PathVariable UUID id, @Valid @RequestBody VariationRequest body) {
        return service.requestVariation(TenantContext.require(), id, body);
    }

    @PostMapping("/{id}/variations/{variationId}/decision")
    public ContractDetail decideVariation(@PathVariable UUID id, @PathVariable UUID variationId,
            @Valid @RequestBody VariationDecisionRequest body) {
        return service.decideVariation(TenantContext.require(), id, variationId, Boolean.TRUE.equals(body.approve()),
                body.note());
    }

    @PostMapping("/{id}/complete")
    public ContractDetail complete(@PathVariable UUID id, @Valid @RequestBody CompleteRequest body) {
        return service.complete(TenantContext.require(), id, body);
    }

    @PostMapping("/{id}/terminate")
    public ContractDetail terminate(@PathVariable UUID id, @Valid @RequestBody ReasonRequest body) {
        return service.terminate(TenantContext.require(), id, body.reason());
    }
}
