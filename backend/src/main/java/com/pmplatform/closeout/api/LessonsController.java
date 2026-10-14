package com.pmplatform.closeout.api;

import com.pmplatform.closeout.ChecklistTemplateService;
import com.pmplatform.closeout.LessonCategory;
import com.pmplatform.closeout.LessonKind;
import com.pmplatform.closeout.LessonService;
import com.pmplatform.closeout.api.CloseoutDtos.LessonView;
import com.pmplatform.closeout.api.CloseoutDtos.ReviewRequest;
import com.pmplatform.closeout.api.CloseoutDtos.TemplateRequest;
import com.pmplatform.closeout.api.CloseoutDtos.TemplateView;
import com.pmplatform.platform.tenant.TenantContext;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The lessons-learned library across projects, and the standard completion checklist. */
@RestController
@RequestMapping("/api/v1")
public class LessonsController {

    private final LessonService lessons;
    private final ChecklistTemplateService templates;

    public LessonsController(LessonService lessons, ChecklistTemplateService templates) {
        this.lessons = lessons;
        this.templates = templates;
    }

    @GetMapping("/lessons")
    public List<LessonView> library(@RequestParam(required = false) String q,
            @RequestParam(required = false) LessonCategory category, @RequestParam(required = false) LessonKind kind) {
        return lessons.library(TenantContext.require(), q, category, kind);
    }

    @PostMapping("/lessons/{lessonId}/review")
    public LessonView review(@PathVariable UUID lessonId, @Valid @RequestBody ReviewRequest body) {
        return lessons.review(TenantContext.require(), lessonId, body.status(), body.note());
    }

    @GetMapping("/settings/closeout-checklist")
    public TemplateView template() {
        return templates.get(TenantContext.require());
    }

    @PutMapping("/settings/closeout-checklist")
    public TemplateView saveTemplate(@Valid @RequestBody TemplateRequest body) {
        return templates.save(TenantContext.require(), body.items());
    }
}
