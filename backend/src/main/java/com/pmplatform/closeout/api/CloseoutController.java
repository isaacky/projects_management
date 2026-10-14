package com.pmplatform.closeout.api;

import com.pmplatform.closeout.CloseoutDocumentKind;
import com.pmplatform.closeout.CloseoutService;
import com.pmplatform.closeout.CloseoutService.Download;
import com.pmplatform.closeout.ItemStatus;
import com.pmplatform.closeout.LessonService;
import com.pmplatform.closeout.api.CloseoutDtos.CloseoutView;
import com.pmplatform.closeout.api.CloseoutDtos.DefectRequest;
import com.pmplatform.closeout.api.CloseoutDtos.HandoverRequest;
import com.pmplatform.closeout.api.CloseoutDtos.ItemRequest;
import com.pmplatform.closeout.api.CloseoutDtos.LessonRequest;
import com.pmplatform.closeout.api.CloseoutDtos.LessonView;
import com.pmplatform.closeout.api.CloseoutDtos.NoteRequest;
import com.pmplatform.platform.tenant.TenantContext;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
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

/** A project's closeout: checklist, defects, handover, documents and lessons learned. */
@RestController
@RequestMapping("/api/v1/projects/{projectId}")
public class CloseoutController {

    private final CloseoutService service;
    private final LessonService lessons;

    public CloseoutController(CloseoutService service, LessonService lessons) {
        this.service = service;
        this.lessons = lessons;
    }

    @GetMapping("/closeout")
    public CloseoutView get(@PathVariable UUID projectId) {
        return service.get(TenantContext.require(), projectId);
    }

    @PostMapping("/closeout/start")
    public CloseoutView start(@PathVariable UUID projectId) {
        return service.start(TenantContext.require(), projectId);
    }

    // ---- handover and closing

    @PutMapping("/closeout/handover")
    public CloseoutView saveHandover(@PathVariable UUID projectId, @Valid @RequestBody HandoverRequest body) {
        return service.saveHandover(TenantContext.require(), projectId, body);
    }

    @PostMapping("/closeout/handover/submit")
    public CloseoutView submitHandover(@PathVariable UUID projectId) {
        return service.submitHandover(TenantContext.require(), projectId);
    }

    @PostMapping("/closeout/handover/accept")
    public CloseoutView acceptHandover(@PathVariable UUID projectId, @Valid @RequestBody(required = false) NoteRequest body) {
        return service.acceptHandover(TenantContext.require(), projectId, body == null ? null : body.note());
    }

    @PostMapping("/closeout/handover/reject")
    public CloseoutView rejectHandover(@PathVariable UUID projectId, @Valid @RequestBody NoteRequest body) {
        return service.rejectHandover(TenantContext.require(), projectId, body.note());
    }

    @PostMapping("/closeout/close")
    public CloseoutView close(@PathVariable UUID projectId, @Valid @RequestBody(required = false) NoteRequest body) {
        return service.close(TenantContext.require(), projectId, body == null ? null : body.note());
    }

    // ---- checklist

    @PostMapping("/closeout/checklist")
    public CloseoutView addItem(@PathVariable UUID projectId, @Valid @RequestBody ItemRequest body) {
        return service.addItem(TenantContext.require(), projectId, body);
    }

    @PutMapping("/closeout/checklist/{itemId}")
    public CloseoutView editItem(@PathVariable UUID projectId, @PathVariable UUID itemId,
            @Valid @RequestBody ItemRequest body) {
        return service.editItem(TenantContext.require(), projectId, itemId, body);
    }

    @DeleteMapping("/closeout/checklist/{itemId}")
    public CloseoutView deleteItem(@PathVariable UUID projectId, @PathVariable UUID itemId) {
        return service.deleteItem(TenantContext.require(), projectId, itemId);
    }

    @PostMapping("/closeout/checklist/{itemId}/done")
    public CloseoutView done(@PathVariable UUID projectId, @PathVariable UUID itemId,
            @Valid @RequestBody(required = false) NoteRequest body) {
        return service.markItem(TenantContext.require(), projectId, itemId, ItemStatus.DONE,
                body == null ? null : body.note());
    }

    @PostMapping("/closeout/checklist/{itemId}/not-applicable")
    public CloseoutView notApplicable(@PathVariable UUID projectId, @PathVariable UUID itemId,
            @Valid @RequestBody NoteRequest body) {
        return service.markItem(TenantContext.require(), projectId, itemId, ItemStatus.NOT_APPLICABLE, body.note());
    }

    @PostMapping("/closeout/checklist/{itemId}/reopen")
    public CloseoutView reopen(@PathVariable UUID projectId, @PathVariable UUID itemId) {
        return service.markItem(TenantContext.require(), projectId, itemId, ItemStatus.OPEN, null);
    }

    // ---- defects

    @PostMapping("/closeout/defects")
    public CloseoutView reportDefect(@PathVariable UUID projectId, @Valid @RequestBody DefectRequest body) {
        return service.reportDefect(TenantContext.require(), projectId, body);
    }

    @PutMapping("/closeout/defects/{defectId}")
    public CloseoutView editDefect(@PathVariable UUID projectId, @PathVariable UUID defectId,
            @Valid @RequestBody DefectRequest body) {
        return service.editDefect(TenantContext.require(), projectId, defectId, body);
    }

    /** action: rectified, verify, reopen or waive. */
    @PostMapping("/closeout/defects/{defectId}/{action}")
    public CloseoutView defectAction(@PathVariable UUID projectId, @PathVariable UUID defectId,
            @PathVariable String action, @Valid @RequestBody(required = false) NoteRequest body) {
        return service.defectAction(TenantContext.require(), projectId, defectId, action,
                body == null ? null : body.note());
    }

    // ---- documents

    @PostMapping(path = "/closeout/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CloseoutView upload(@PathVariable UUID projectId, @RequestParam CloseoutDocumentKind kind,
            @RequestParam(required = false) UUID refId, @RequestPart("file") MultipartFile file) {
        return service.upload(TenantContext.require(), projectId, kind, refId, file);
    }

    @GetMapping("/closeout/documents/{documentId}")
    public ResponseEntity<InputStreamResource> document(@PathVariable UUID projectId, @PathVariable UUID documentId) {
        Download d = service.document(TenantContext.require(), projectId, documentId);
        MediaType type;
        try {
            type = MediaType.parseMediaType(d.contentType());
        } catch (RuntimeException e) {
            type = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok().contentType(type).contentLength(d.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(d.fileName(), StandardCharsets.UTF_8).build().toString())
                .body(new InputStreamResource(d.content()));
    }

    @DeleteMapping("/closeout/documents/{documentId}")
    public CloseoutView deleteDocument(@PathVariable UUID projectId, @PathVariable UUID documentId) {
        return service.deleteDocument(TenantContext.require(), projectId, documentId);
    }

    // ---- lessons (any phase)

    @PostMapping("/lessons")
    public LessonView addLesson(@PathVariable UUID projectId, @Valid @RequestBody LessonRequest body) {
        return lessons.add(TenantContext.require(), projectId, body);
    }

    @PutMapping("/lessons/{lessonId}")
    public LessonView editLesson(@PathVariable UUID projectId, @PathVariable UUID lessonId,
            @Valid @RequestBody LessonRequest body) {
        return lessons.edit(TenantContext.require(), projectId, lessonId, body);
    }
}
