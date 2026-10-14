package com.pmplatform.procurement.config;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Applies a tenant's procurement settings to a purchase. Pure functions over the
 * configuration rows, so the rules are tested without a database.
 */
public final class ProcurementRules {

    private ProcurementRules() {
    }

    public record ApprovalStep(int step, String approverRole) {
    }

    /** Enabled methods whose value range covers the amount, in display order. */
    public static List<ProcurementMethod> allowedMethods(List<ProcurementMethod> methods,
            long amountMinor, String currency) {
        return methods.stream()
                .filter(ProcurementMethod::isEnabled)
                .filter(m -> m.covers(amountMinor, currency))
                .sorted(Comparator.comparingInt(ProcurementMethod::getSortOrder)
                        .thenComparing(ProcurementMethod::getCode))
                .toList();
    }

    /**
     * The approval chain for an amount at one stage. Bands configured for the specific
     * method win; otherwise the general bands (no method) apply.
     */
    public static List<ApprovalStep> approvers(List<ApprovalThreshold> stageRows, String methodCode,
            long amountMinor, String currency) {
        List<ApprovalThreshold> matching = stageRows.stream()
                .filter(ApprovalThreshold::isActive)
                .filter(t -> t.getCurrency().equals(currency))
                .filter(t -> t.covers(amountMinor))
                .toList();

        List<ApprovalThreshold> specific = methodCode == null ? List.of()
                : matching.stream().filter(t -> methodCode.equals(t.getMethodCode())).toList();
        List<ApprovalThreshold> chosen = !specific.isEmpty() ? specific
                : matching.stream().filter(t -> t.getMethodCode() == null).toList();

        return chosen.stream()
                .sorted(Comparator.comparingInt(ApprovalThreshold::getStepOrder))
                .map(t -> new ApprovalStep(t.getStepOrder(), t.getApproverRole()))
                .toList();
    }

    /** The method's own committee rule, or the tenant default. */
    public static Optional<EvaluationCommitteeRule> committeeRule(List<EvaluationCommitteeRule> rules,
            String methodCode) {
        Optional<EvaluationCommitteeRule> specific = methodCode == null ? Optional.empty()
                : rules.stream().filter(r -> methodCode.equals(r.getMethodCode())).findFirst();
        return specific.or(() -> rules.stream().filter(EvaluationCommitteeRule::isDefault).findFirst());
    }

    /**
     * An active row with the same stage, method, currency and step whose band overlaps
     * the candidate band. Two such rows would make the approver for an amount ambiguous.
     */
    public static Optional<ApprovalThreshold> conflictingBand(List<ApprovalThreshold> stageRows,
            Object excludeId, String methodCode, String currency, int stepOrder, long min, Long max) {
        return stageRows.stream()
                .filter(ApprovalThreshold::isActive)
                .filter(t -> !t.getId().equals(excludeId))
                .filter(t -> Objects.equals(t.getMethodCode(), methodCode))
                .filter(t -> t.getCurrency().equals(currency))
                .filter(t -> t.getStepOrder() == stepOrder)
                .filter(t -> t.overlaps(min, max))
                .findFirst();
    }
}
