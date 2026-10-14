package com.pmplatform.planning.api;

import com.pmplatform.planning.PlanningService;
import com.pmplatform.planning.api.PlanningDtos.BaselineRequest;
import com.pmplatform.planning.api.PlanningDtos.ProgressRequest;
import com.pmplatform.planning.api.PlanningDtos.SaveScheduleRequest;
import com.pmplatform.planning.api.PlanningDtos.ScheduleView;
import com.pmplatform.platform.tenant.TenantContext;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** A project's schedule. */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/schedule")
public class PlanningController {

    private final PlanningService service;

    public PlanningController(PlanningService service) {
        this.service = service;
    }

    @GetMapping
    public ScheduleView get(@PathVariable UUID projectId) {
        return service.view(TenantContext.require(), projectId);
    }

    /** Saves the whole schedule (rows in display order). */
    @PutMapping
    public ScheduleView save(@PathVariable UUID projectId, @Valid @RequestBody SaveScheduleRequest body) {
        return service.save(TenantContext.require(), projectId, body);
    }

    @PostMapping("/baseline")
    public ScheduleView baseline(@PathVariable UUID projectId, @Valid @RequestBody BaselineRequest body) {
        return service.baseline(TenantContext.require(), projectId, body.note());
    }

    @PutMapping("/activities/{activityId}/progress")
    public ScheduleView progress(@PathVariable UUID projectId, @PathVariable UUID activityId,
            @Valid @RequestBody ProgressRequest body) {
        return service.progress(TenantContext.require(), projectId, activityId, body);
    }

    @PostMapping(path = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ScheduleView importMsProject(@PathVariable UUID projectId, @RequestPart("file") MultipartFile file) {
        return service.importMsProject(TenantContext.require(), projectId, file);
    }
}
