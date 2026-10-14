package com.pmplatform.execution.api;

import com.pmplatform.execution.ExecutionService;
import com.pmplatform.execution.api.ExecutionDtos.CreateReportRequest;
import com.pmplatform.execution.api.ExecutionDtos.IssueRequest;
import com.pmplatform.execution.api.ExecutionDtos.IssueView;
import com.pmplatform.execution.api.ExecutionDtos.NoteRequest;
import com.pmplatform.execution.api.ExecutionDtos.ProjectHealth;
import com.pmplatform.execution.api.ExecutionDtos.ReportRequest;
import com.pmplatform.execution.api.ExecutionDtos.ReportView;
import com.pmplatform.execution.api.ExecutionDtos.RiskRequest;
import com.pmplatform.execution.api.ExecutionDtos.RiskView;
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
import org.springframework.web.bind.annotation.RestController;

/** Risks, issues and status reports of a project, and portfolio health. */
@RestController
@RequestMapping("/api/v1")
public class ExecutionController {

    private final ExecutionService service;

    public ExecutionController(ExecutionService service) {
        this.service = service;
    }

    @GetMapping("/portfolio/health")
    public List<ProjectHealth> health() {
        return service.portfolioHealth(TenantContext.require());
    }

    // ---- risks

    @GetMapping("/projects/{projectId}/risks")
    public List<RiskView> risks(@PathVariable UUID projectId) {
        return service.risks(TenantContext.require(), projectId);
    }

    @PostMapping("/projects/{projectId}/risks")
    public RiskView createRisk(@PathVariable UUID projectId, @Valid @RequestBody RiskRequest body) {
        return service.createRisk(TenantContext.require(), projectId, body);
    }

    @PutMapping("/projects/{projectId}/risks/{riskId}")
    public RiskView updateRisk(@PathVariable UUID projectId, @PathVariable UUID riskId, @Valid @RequestBody RiskRequest body) {
        return service.updateRisk(TenantContext.require(), projectId, riskId, body);
    }

    @PostMapping("/projects/{projectId}/risks/{riskId}/close")
    public RiskView closeRisk(@PathVariable UUID projectId, @PathVariable UUID riskId, @Valid @RequestBody NoteRequest body) {
        return service.closeRisk(TenantContext.require(), projectId, riskId, body.note(), false);
    }

    @PostMapping("/projects/{projectId}/risks/{riskId}/reopen")
    public RiskView reopenRisk(@PathVariable UUID projectId, @PathVariable UUID riskId) {
        return service.closeRisk(TenantContext.require(), projectId, riskId, null, true);
    }

    // ---- issues

    @GetMapping("/projects/{projectId}/issues")
    public List<IssueView> issues(@PathVariable UUID projectId) {
        return service.issues(TenantContext.require(), projectId);
    }

    @PostMapping("/projects/{projectId}/issues")
    public IssueView createIssue(@PathVariable UUID projectId, @Valid @RequestBody IssueRequest body) {
        return service.saveIssue(TenantContext.require(), projectId, null, body);
    }

    @PutMapping("/projects/{projectId}/issues/{issueId}")
    public IssueView updateIssue(@PathVariable UUID projectId, @PathVariable UUID issueId,
            @Valid @RequestBody IssueRequest body) {
        return service.saveIssue(TenantContext.require(), projectId, issueId, body);
    }

    // ---- status reports

    @GetMapping("/projects/{projectId}/status-reports")
    public List<ReportView> reports(@PathVariable UUID projectId) {
        return service.reports(TenantContext.require(), projectId);
    }

    @PostMapping("/projects/{projectId}/status-reports")
    public ReportView createReport(@PathVariable UUID projectId, @Valid @RequestBody CreateReportRequest body) {
        return service.createReport(TenantContext.require(), projectId, body.periodEnd());
    }

    @PutMapping("/projects/{projectId}/status-reports/{reportId}")
    public ReportView editReport(@PathVariable UUID projectId, @PathVariable UUID reportId,
            @Valid @RequestBody ReportRequest body) {
        return service.editReport(TenantContext.require(), projectId, reportId, body);
    }

    @PostMapping("/projects/{projectId}/status-reports/{reportId}/submit")
    public ReportView submit(@PathVariable UUID projectId, @PathVariable UUID reportId) {
        return service.submitReport(TenantContext.require(), projectId, reportId);
    }

    @PostMapping("/projects/{projectId}/status-reports/{reportId}/review")
    public ReportView review(@PathVariable UUID projectId, @PathVariable UUID reportId,
            @Valid @RequestBody NoteRequest body) {
        return service.reviewReport(TenantContext.require(), projectId, reportId, body.note());
    }
}
