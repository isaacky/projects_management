package com.pmplatform.procurement.plan.api;

import com.pmplatform.platform.tenant.TenantContext;
import com.pmplatform.procurement.plan.ProcurementPlanService;
import com.pmplatform.procurement.plan.api.PlanDtos.AvailableLine;
import com.pmplatform.procurement.plan.api.PlanDtos.CreatePlanRequest;
import com.pmplatform.procurement.plan.api.PlanDtos.DepartmentRequest;
import com.pmplatform.procurement.plan.api.PlanDtos.ImportResult;
import com.pmplatform.procurement.plan.api.PlanDtos.LineRequest;
import com.pmplatform.procurement.plan.api.PlanDtos.NoteRequest;
import com.pmplatform.procurement.plan.api.PlanDtos.PlanSummary;
import com.pmplatform.procurement.plan.api.PlanDtos.PlanView;
import com.pmplatform.procurement.plan.api.PlanDtos.SettingsRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
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

/** The annual procurement plan. */
@RestController
@RequestMapping("/api/v1/procurement-plans")
public class ProcurementPlanController {

    private final ProcurementPlanService service;

    public ProcurementPlanController(ProcurementPlanService service) {
        this.service = service;
    }

    @GetMapping
    public List<PlanSummary> list() {
        return service.list(TenantContext.require());
    }

    /** Approved lines of the current financial year's plan, for the requisition form. */
    @GetMapping("/available-lines")
    public List<AvailableLine> availableLines(@RequestParam(required = false) UUID projectId) {
        return service.availableLines(TenantContext.require(), projectId);
    }

    @PostMapping
    public PlanView create(@Valid @RequestBody CreatePlanRequest body) {
        return service.create(TenantContext.require(), body);
    }

    @GetMapping("/{planId}")
    public PlanView get(@PathVariable UUID planId) {
        return service.get(TenantContext.require(), planId);
    }

    @PutMapping("/{planId}/settings")
    public PlanView settings(@PathVariable UUID planId, @Valid @RequestBody SettingsRequest body) {
        return service.updateSettings(TenantContext.require(), planId, body.agpoTargetPct());
    }

    @PostMapping("/{planId}/submit")
    public PlanView submit(@PathVariable UUID planId) {
        return service.submit(TenantContext.require(), planId);
    }

    @PostMapping("/{planId}/approve")
    public PlanView approve(@PathVariable UUID planId, @Valid @RequestBody(required = false) NoteRequest body) {
        return service.approve(TenantContext.require(), planId, body == null ? null : body.note());
    }

    @PostMapping("/{planId}/return")
    public PlanView returnPlan(@PathVariable UUID planId, @Valid @RequestBody NoteRequest body) {
        return service.returnPlan(TenantContext.require(), planId, body.note());
    }

    @PostMapping("/{planId}/revise")
    public PlanView revise(@PathVariable UUID planId, @Valid @RequestBody NoteRequest body) {
        return service.revise(TenantContext.require(), planId, body.note());
    }

    // ---------------------------------------------------------------- departments

    @PostMapping("/{planId}/departments")
    public PlanView addDepartment(@PathVariable UUID planId, @Valid @RequestBody DepartmentRequest body) {
        return service.addDepartment(TenantContext.require(), planId, body);
    }

    @PutMapping("/{planId}/departments/{deptId}")
    public PlanView editDepartment(@PathVariable UUID planId, @PathVariable UUID deptId,
            @Valid @RequestBody DepartmentRequest body) {
        return service.editDepartment(TenantContext.require(), planId, deptId, body);
    }

    @DeleteMapping("/{planId}/departments/{deptId}")
    public PlanView deleteDepartment(@PathVariable UUID planId, @PathVariable UUID deptId) {
        return service.deleteDepartment(TenantContext.require(), planId, deptId);
    }

    @PostMapping("/{planId}/departments/{deptId}/submit")
    public PlanView submitDepartment(@PathVariable UUID planId, @PathVariable UUID deptId) {
        return service.submitDepartment(TenantContext.require(), planId, deptId);
    }

    @PostMapping("/{planId}/departments/{deptId}/return")
    public PlanView returnDepartment(@PathVariable UUID planId, @PathVariable UUID deptId,
            @Valid @RequestBody NoteRequest body) {
        return service.returnDepartment(TenantContext.require(), planId, deptId, body.note());
    }

    @PostMapping(path = "/{planId}/departments/{deptId}/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportResult importLines(@PathVariable UUID planId, @PathVariable UUID deptId,
            @RequestPart("file") MultipartFile file) {
        return service.importLines(TenantContext.require(), planId, deptId, file);
    }

    // ---------------------------------------------------------------- lines

    @PostMapping("/{planId}/lines")
    public PlanView addLine(@PathVariable UUID planId, @Valid @RequestBody LineRequest body) {
        return service.addLine(TenantContext.require(), planId, body);
    }

    @PutMapping("/{planId}/lines/{lineId}")
    public PlanView editLine(@PathVariable UUID planId, @PathVariable UUID lineId,
            @Valid @RequestBody LineRequest body) {
        return service.editLine(TenantContext.require(), planId, lineId, body);
    }

    @DeleteMapping("/{planId}/lines/{lineId}")
    public PlanView deleteLine(@PathVariable UUID planId, @PathVariable UUID lineId) {
        return service.deleteLine(TenantContext.require(), planId, lineId);
    }

    @PostMapping("/{planId}/lines/{lineId}/cancel")
    public PlanView cancelLine(@PathVariable UUID planId, @PathVariable UUID lineId,
            @Valid @RequestBody NoteRequest body) {
        return service.cancelLine(TenantContext.require(), planId, lineId, body.note());
    }
}
