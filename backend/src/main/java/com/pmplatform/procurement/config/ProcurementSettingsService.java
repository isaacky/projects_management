package com.pmplatform.procurement.config;

import com.pmplatform.platform.audit.AuditService;
import com.pmplatform.platform.json.JsonText;
import com.pmplatform.platform.web.BusinessRuleException;
import com.pmplatform.platform.web.NotFoundException;
import com.pmplatform.procurement.config.ProcurementRules.ApprovalStep;
import com.pmplatform.procurement.config.api.SettingsDtos.CheckResponse;
import com.pmplatform.procurement.config.api.SettingsDtos.CommitteeRuleRequest;
import com.pmplatform.procurement.config.api.SettingsDtos.CommitteeRuleResponse;
import com.pmplatform.procurement.config.api.SettingsDtos.MethodRequest;
import com.pmplatform.procurement.config.api.SettingsDtos.MethodResponse;
import com.pmplatform.procurement.config.api.SettingsDtos.SettingsResponse;
import com.pmplatform.procurement.config.api.SettingsDtos.TemplateRequest;
import com.pmplatform.procurement.config.api.SettingsDtos.TemplateResponse;
import com.pmplatform.procurement.config.api.SettingsDtos.ThresholdRequest;
import com.pmplatform.procurement.config.api.SettingsDtos.ThresholdResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads and changes a tenant's procurement settings. Every change is validated,
 * then written to the audit log in the same transaction.
 */
@Service
public class ProcurementSettingsService {

    private final ProcurementMethodRepository methods;
    private final ApprovalThresholdRepository thresholds;
    private final EvaluationCommitteeRuleRepository committees;
    private final EvaluationTemplateRepository templates;
    private final AuditService audit;
    private final Clock clock;

    public ProcurementSettingsService(ProcurementMethodRepository methods,
            ApprovalThresholdRepository thresholds, EvaluationCommitteeRuleRepository committees,
            EvaluationTemplateRepository templates, AuditService audit, Clock clock) {
        this.methods = methods;
        this.thresholds = thresholds;
        this.committees = committees;
        this.templates = templates;
        this.audit = audit;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ read

    @Transactional(readOnly = true)
    public SettingsResponse all(UUID tenantId) {
        return new SettingsResponse(
                methods.findByTenantIdOrderBySortOrderAscCodeAsc(tenantId).stream()
                        .map(MethodResponse::from).toList(),
                thresholds.findByTenantIdOrderByStageAscMethodCodeAscMinAmountMinorAscStepOrderAsc(tenantId)
                        .stream().map(ThresholdResponse::from).toList(),
                committees.findByTenantId(tenantId).stream()
                        .sorted(Comparator.comparing((EvaluationCommitteeRule r) -> r.getMethodCode() != null)
                                .thenComparing(r -> r.getMethodCode() == null ? "" : r.getMethodCode()))
                        .map(CommitteeRuleResponse::from).toList(),
                templates.findByTenantIdOrderByCodeAsc(tenantId).stream().map(TemplateResponse::from).toList());
    }

    /** Applies the settings to one purchase, for the settings page and for later workflows. */
    @Transactional(readOnly = true)
    public CheckResponse check(UUID tenantId, ApprovalStage stage, String methodCode,
            long amountMinor, String currency) {
        List<String> warnings = new ArrayList<>();
        List<ProcurementMethod> allowed = ProcurementRules.allowedMethods(
                methods.findByTenantIdOrderBySortOrderAscCodeAsc(tenantId), amountMinor, currency);
        boolean methodAllowed = methodCode != null
                && allowed.stream().anyMatch(m -> m.getCode().equals(methodCode));
        if (methodCode != null && !methodAllowed) {
            warnings.add(methodCode + " is not enabled for this amount");
        }

        List<ApprovalStep> approvers = ProcurementRules.approvers(
                thresholds.findByTenantIdAndStage(tenantId, stage), methodCode, amountMinor, currency);
        if (approvers.isEmpty()) {
            warnings.add("No approver is configured for " + stage + " at this amount");
        }

        CommitteeRuleResponse committee = ProcurementRules
                .committeeRule(committees.findByTenantId(tenantId), methodCode)
                .map(CommitteeRuleResponse::from)
                .orElse(null);
        if (committee == null) {
            warnings.add("No evaluation committee rule is configured");
        }

        return new CheckResponse(methodCode, methodAllowed,
                allowed.stream().map(ProcurementMethod::getCode).toList(),
                approvers, committee, warnings);
    }

    // ------------------------------------------------------------------ methods

    @Transactional
    public MethodResponse saveMethod(UUID tenantId, String code, MethodRequest req) {
        if (req.minValueMinor() != null && req.maxValueMinor() != null
                && req.minValueMinor() > req.maxValueMinor()) {
            throw new BusinessRuleException("Minimum value cannot be above the maximum value");
        }
        Instant now = Instant.now(clock);
        ProcurementMethod method = methods.findByTenantIdAndCode(tenantId, code)
                .orElseGet(() -> new ProcurementMethod(tenantId, code, now));
        boolean created = method.isNew();
        method.update(req.name().trim(), blankToNull(req.description()), req.enabled(),
                req.minValueMinor(), req.maxValueMinor(), req.currency(), req.minBidPeriodDays(),
                req.requiresPublicAdvert(), req.requiresJustification(), req.sortOrder(), now);
        if (req.standstillDays() != null) {
            method.setStandstillDays(req.standstillDays(), now);
        }
        methods.save(method);
        audit.record(tenantId, created ? "procurement.method.created" : "procurement.method.updated",
                "ProcurementMethod", method.getId(),
                JsonText.object("code", code, "enabled", req.enabled(),
                        "minValueMinor", req.minValueMinor(), "maxValueMinor", req.maxValueMinor(),
                        "currency", req.currency()));
        return MethodResponse.from(method);
    }

    // ------------------------------------------------------------------ thresholds

    @Transactional
    public ThresholdResponse createThreshold(UUID tenantId, ThresholdRequest req) {
        ApprovalThreshold row = new ApprovalThreshold(tenantId, Instant.now(clock));
        apply(tenantId, row, req);
        thresholds.save(row);
        auditThreshold(tenantId, "procurement.approval_threshold.created", row);
        return ThresholdResponse.from(row);
    }

    @Transactional
    public ThresholdResponse updateThreshold(UUID tenantId, UUID id, ThresholdRequest req) {
        ApprovalThreshold row = thresholds.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new NotFoundException("Approval threshold " + id + " not found"));
        apply(tenantId, row, req);
        auditThreshold(tenantId, "procurement.approval_threshold.updated", row);
        return ThresholdResponse.from(row);
    }

    @Transactional
    public void deleteThreshold(UUID tenantId, UUID id) {
        ApprovalThreshold row = thresholds.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new NotFoundException("Approval threshold " + id + " not found"));
        auditThreshold(tenantId, "procurement.approval_threshold.deleted", row);
        thresholds.delete(row);
    }

    private void apply(UUID tenantId, ApprovalThreshold row, ThresholdRequest req) {
        String methodCode = blankToNull(req.methodCode());
        if (req.maxAmountMinor() != null && req.minAmountMinor() >= req.maxAmountMinor()) {
            throw new BusinessRuleException("The band's upper limit must be above its lower limit");
        }
        if (methodCode != null) {
            requireMethod(tenantId, methodCode);
        }
        if (req.active()) {
            ProcurementRules.conflictingBand(thresholds.findByTenantIdAndStage(tenantId, req.stage()),
                    row.getId(), methodCode, req.currency(), req.stepOrder(), req.minAmountMinor(),
                    req.maxAmountMinor())
                    .ifPresent(other -> {
                        throw new BusinessRuleException("Step " + req.stepOrder()
                                + " overlaps an existing band for " + req.stage()
                                + (methodCode == null ? "" : " / " + methodCode)
                                + " (approver " + other.getApproverRole() + ")");
                    });
        }
        row.update(req.stage(), methodCode, req.minAmountMinor(), req.maxAmountMinor(),
                req.currency(), req.stepOrder(), req.approverRole(), req.active(), Instant.now(clock));
    }

    private void auditThreshold(UUID tenantId, String action, ApprovalThreshold t) {
        audit.record(tenantId, action, "ApprovalThreshold", t.getId(),
                JsonText.object("stage", t.getStage(), "methodCode", t.getMethodCode(),
                        "minAmountMinor", t.getMinAmountMinor(), "maxAmountMinor", t.getMaxAmountMinor(),
                        "currency", t.getCurrency(), "stepOrder", t.getStepOrder(),
                        "approverRole", t.getApproverRole(), "active", t.isActive()));
    }

    // ------------------------------------------------------------------ committees

    /** {@code methodCode} null = the tenant's default rule. */
    @Transactional
    public CommitteeRuleResponse saveCommitteeRule(UUID tenantId, String methodCode,
            CommitteeRuleRequest req) {
        if (req.minMembers() > req.maxMembers()) {
            throw new BusinessRuleException("Minimum members cannot be above maximum members");
        }
        if (req.quorum() > req.minMembers()) {
            throw new BusinessRuleException("Quorum cannot be larger than the minimum committee size");
        }
        if (methodCode != null) {
            requireMethod(tenantId, methodCode);
        }
        Instant now = Instant.now(clock);
        EvaluationCommitteeRule rule = (methodCode == null
                ? committees.findByTenantIdAndMethodCodeIsNull(tenantId)
                : committees.findByTenantIdAndMethodCode(tenantId, methodCode))
                .orElseGet(() -> new EvaluationCommitteeRule(tenantId, methodCode, now));
        boolean created = rule.isNew();
        rule.update(req.minMembers(), req.maxMembers(), req.quorum(), req.twoEnvelope(),
                req.technicalPassMarkPct(), req.requiresCoiDeclaration(), req.reportSignatories(),
                blankToNull(req.reportReviewerRole()), req.awardApproverRole(), now);
        committees.save(rule);
        audit.record(tenantId,
                created ? "procurement.committee_rule.created" : "procurement.committee_rule.updated",
                "EvaluationCommitteeRule", rule.getId(),
                JsonText.object("methodCode", methodCode, "minMembers", req.minMembers(),
                        "maxMembers", req.maxMembers(), "quorum", req.quorum(),
                        "twoEnvelope", req.twoEnvelope(), "technicalPassMarkPct", req.technicalPassMarkPct(),
                        "reportSignatories", req.reportSignatories(),
                        "awardApproverRole", req.awardApproverRole()));
        return CommitteeRuleResponse.from(rule);
    }

    @Transactional
    public void deleteCommitteeRule(UUID tenantId, String methodCode) {
        if (methodCode == null) {
            throw new BusinessRuleException("The default committee rule can be changed but not removed");
        }
        EvaluationCommitteeRule rule = committees.findByTenantIdAndMethodCode(tenantId, methodCode)
                .orElseThrow(() -> new NotFoundException("No committee rule for " + methodCode));
        audit.record(tenantId, "procurement.committee_rule.deleted", "EvaluationCommitteeRule",
                rule.getId(), JsonText.object("methodCode", methodCode));
        committees.delete(rule);
    }

    // ------------------------------------------------------------------ evaluation templates

    @Transactional
    public TemplateResponse saveTemplate(UUID tenantId, String code, TemplateRequest req) {
        String methodCode = blankToNull(req.methodCode());
        if (methodCode != null) {
            requireMethod(tenantId, methodCode);
        }
        Instant now = Instant.now(clock);
        EvaluationTemplate t = templates.findByTenantIdAndCode(tenantId, code)
                .orElseGet(() -> new EvaluationTemplate(tenantId, code, now));
        boolean created = t.isNew();
        boolean active = !Boolean.FALSE.equals(req.active());
        t.update(req.name().trim(), methodCode, blankToNull(req.description()), active,
                req.criteria().stream().map(c -> new EvaluationTemplate.CriterionInput(c.stage(), c.name(),
                        c.description(), c.maxScore())).toList(), now);
        templates.save(t);
        audit.record(tenantId, created ? "procurement.evaluation_template.created"
                : "procurement.evaluation_template.updated", "EvaluationTemplate", t.getId(),
                JsonText.object("code", code, "methodCode", methodCode, "active", active,
                        "criteria", t.getCriteria().size(), "technicalTotal", t.technicalTotal()));
        return TemplateResponse.from(t);
    }

    @Transactional
    public void deleteTemplate(UUID tenantId, String code) {
        EvaluationTemplate t = templates.findByTenantIdAndCode(tenantId, code)
                .orElseThrow(() -> new NotFoundException("No evaluation template " + code));
        audit.record(tenantId, "procurement.evaluation_template.deleted", "EvaluationTemplate", t.getId(),
                JsonText.object("code", code));
        templates.delete(t);
    }

    // ------------------------------------------------------------------ helpers

    private void requireMethod(UUID tenantId, String code) {
        if (methods.findByTenantIdAndCode(tenantId, code).isEmpty()) {
            throw new BusinessRuleException("Unknown procurement method " + code
                    + "; add it under procurement methods first");
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
