package com.pmplatform.procurement.config.api;

import com.pmplatform.procurement.config.ApprovalStage;
import com.pmplatform.procurement.config.ApprovalThreshold;
import com.pmplatform.procurement.config.CriterionStage;
import com.pmplatform.procurement.config.EvaluationTemplate;
import com.pmplatform.procurement.config.EvaluationCommitteeRule;
import com.pmplatform.procurement.config.ProcurementMethod;
import com.pmplatform.procurement.config.ProcurementRules.ApprovalStep;
import com.pmplatform.procurement.config.ReportSignatories;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Request and response bodies for the procurement settings API. */
public final class SettingsDtos {

    static final String ROLE_PATTERN = "[A-Z][A-Z0-9_]*";
    static final String ROLE_MESSAGE = "upper-case role code, e.g. ACCOUNTING_OFFICER";
    static final String CURRENCY_PATTERN = "[A-Z]{3}";

    private SettingsDtos() {
    }

    // ---------------------------------------------------------------- methods
    public record MethodRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 1000) String description,
            boolean enabled,
            @PositiveOrZero Long minValueMinor,
            @PositiveOrZero Long maxValueMinor,
            @NotBlank @Pattern(regexp = CURRENCY_PATTERN) String currency,
            @Min(0) @Max(365) int minBidPeriodDays,
            boolean requiresPublicAdvert,
            boolean requiresJustification,
            int sortOrder,
            @Min(0) @Max(90) Integer standstillDays) {
    }

    public record MethodResponse(UUID id, String code, String name, String description,
            boolean enabled, Long minValueMinor, Long maxValueMinor, String currency,
            int minBidPeriodDays, boolean requiresPublicAdvert, boolean requiresJustification,
            int sortOrder, int standstillDays, Instant updatedAt, long version) {

        public static MethodResponse from(ProcurementMethod m) {
            return new MethodResponse(m.getId(), m.getCode(), m.getName(), m.getDescription(),
                    m.isEnabled(), m.getMinValueMinor(), m.getMaxValueMinor(), m.getCurrency(),
                    m.getMinBidPeriodDays(), m.isRequiresPublicAdvert(), m.isRequiresJustification(),
                    m.getSortOrder(), m.getStandstillDays(), m.getUpdatedAt(), m.getVersion());
        }
    }

    // ---------------------------------------------------------------- thresholds
    public record ThresholdRequest(
            @NotNull ApprovalStage stage,
            @Pattern(regexp = ROLE_PATTERN, message = "method code, e.g. OPEN_TENDER") String methodCode,
            @PositiveOrZero long minAmountMinor,
            @PositiveOrZero Long maxAmountMinor,
            @NotBlank @Pattern(regexp = CURRENCY_PATTERN) String currency,
            @Min(1) @Max(20) int stepOrder,
            @NotBlank @Size(max = 64) @Pattern(regexp = ROLE_PATTERN, message = ROLE_MESSAGE)
            String approverRole,
            boolean active) {
    }

    public record ThresholdResponse(UUID id, ApprovalStage stage, String methodCode,
            long minAmountMinor, Long maxAmountMinor, String currency, int stepOrder,
            String approverRole, boolean active, Instant updatedAt, long version) {

        public static ThresholdResponse from(ApprovalThreshold t) {
            return new ThresholdResponse(t.getId(), t.getStage(), t.getMethodCode(),
                    t.getMinAmountMinor(), t.getMaxAmountMinor(), t.getCurrency(), t.getStepOrder(),
                    t.getApproverRole(), t.isActive(), t.getUpdatedAt(), t.getVersion());
        }
    }

    // ---------------------------------------------------------------- committees
    public record CommitteeRuleRequest(
            @Min(1) @Max(25) int minMembers,
            @Min(1) @Max(25) int maxMembers,
            @Min(1) @Max(25) int quorum,
            boolean twoEnvelope,
            @Min(0) @Max(100) Integer technicalPassMarkPct,
            boolean requiresCoiDeclaration,
            @NotNull ReportSignatories reportSignatories,
            @Size(max = 64) @Pattern(regexp = ROLE_PATTERN, message = ROLE_MESSAGE)
            String reportReviewerRole,
            @NotBlank @Size(max = 64) @Pattern(regexp = ROLE_PATTERN, message = ROLE_MESSAGE)
            String awardApproverRole) {
    }

    /** {@code methodCode} null = the tenant's default rule. */
    public record CommitteeRuleResponse(UUID id, String methodCode, int minMembers, int maxMembers,
            int quorum, boolean twoEnvelope, Integer technicalPassMarkPct,
            boolean requiresCoiDeclaration, ReportSignatories reportSignatories,
            String reportReviewerRole, String awardApproverRole, Instant updatedAt, long version) {

        public static CommitteeRuleResponse from(EvaluationCommitteeRule r) {
            return new CommitteeRuleResponse(r.getId(), r.getMethodCode(), r.getMinMembers(),
                    r.getMaxMembers(), r.getQuorum(), r.isTwoEnvelope(), r.getTechnicalPassMarkPct(),
                    r.isRequiresCoiDeclaration(), r.getReportSignatories(), r.getReportReviewerRole(),
                    r.getAwardApproverRole(), r.getUpdatedAt(), r.getVersion());
        }
    }

    // ---------------------------------------------------------------- evaluation templates
    public record TemplateCriterionRequest(
            @NotNull CriterionStage stage,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 1000) String description,
            @DecimalMin("0.01") @DecimalMax("9999.99") BigDecimal maxScore) {
    }

    public record TemplateRequest(
            @NotBlank @Size(max = 120) String name,
            @Pattern(regexp = ROLE_PATTERN, message = "method code, e.g. OPEN_TENDER") String methodCode,
            @Size(max = 1000) String description,
            Boolean active,
            @NotEmpty @Size(max = 60) List<@Valid TemplateCriterionRequest> criteria) {
    }

    public record TemplateCriterionResponse(int sortOrder, CriterionStage stage, String name, String description,
            BigDecimal maxScore) {
    }

    public record TemplateResponse(UUID id, String code, String name, String methodCode, String description,
            boolean active, BigDecimal technicalTotal, List<TemplateCriterionResponse> criteria, Instant updatedAt,
            long version) {

        public static TemplateResponse from(EvaluationTemplate t) {
            return new TemplateResponse(t.getId(), t.getCode(), t.getName(), t.getMethodCode(), t.getDescription(),
                    t.isActive(), t.technicalTotal(), t.getCriteria().stream()
                            .map(c -> new TemplateCriterionResponse(c.getSortOrder(), c.getStage(), c.getName(),
                                    c.getDescription(), c.getMaxScore())).toList(),
                    t.getUpdatedAt(), t.getVersion());
        }
    }

    // ---------------------------------------------------------------- all + check
    public record SettingsResponse(List<MethodResponse> methods,
            List<ThresholdResponse> approvalThresholds, List<CommitteeRuleResponse> committeeRules,
            List<TemplateResponse> evaluationTemplates) {
    }

    /** What the settings say about one purchase: method allowed? who approves? which committee? */
    public record CheckResponse(String methodCode, boolean methodAllowed, List<String> allowedMethods,
            List<ApprovalStep> approvers, CommitteeRuleResponse committeeRule,
            List<String> warnings) {
    }
}
