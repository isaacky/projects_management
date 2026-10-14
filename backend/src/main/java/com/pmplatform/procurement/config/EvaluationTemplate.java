package com.pmplatform.procurement.config;

import com.pmplatform.platform.persistence.TenantEntity;
import com.pmplatform.platform.web.BusinessRuleException;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A named set of evaluation criteria (settings). When a tender's evaluation is set up the
 * criteria are copied onto it, so later changes to the template never alter an evaluation.
 */
@Entity
@Table(schema = "procurement", name = "evaluation_templates")
public class EvaluationTemplate extends TenantEntity {

    @Embeddable
    public static class Criterion {

        @Column(name = "sort_order", nullable = false)
        private int sortOrder;

        @Enumerated(EnumType.STRING)
        @Column(nullable = false)
        private CriterionStage stage;

        @Column(nullable = false)
        private String name;

        private String description;

        @Column(name = "max_score", precision = 6, scale = 2)
        private BigDecimal maxScore;

        protected Criterion() {
        }

        public Criterion(int sortOrder, CriterionStage stage, String name, String description, BigDecimal maxScore) {
            this.sortOrder = sortOrder;
            this.stage = stage;
            this.name = name;
            this.description = description;
            this.maxScore = maxScore;
        }

        public int getSortOrder() { return sortOrder; }
        public CriterionStage getStage() { return stage; }
        public String getName() { return name; }
        public String getDescription() { return description; }
        public BigDecimal getMaxScore() { return maxScore; }
    }

    @Column(nullable = false, updatable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "method_code")
    private String methodCode;

    private String description;

    @Column(nullable = false)
    private boolean active;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(schema = "procurement", name = "evaluation_template_criteria",
            joinColumns = @JoinColumn(name = "template_id"))
    @OrderBy("sortOrder")
    private List<Criterion> criteria = new ArrayList<>();

    protected EvaluationTemplate() {
    }

    public EvaluationTemplate(UUID tenantId, String code, Instant now) {
        super(tenantId, now);
        this.code = code;
    }

    /** One stage's criterion as entered: name, optional description, max score for technical ones. */
    public record CriterionInput(CriterionStage stage, String name, String description, BigDecimal maxScore) {
    }

    public void update(String name, String methodCode, String description, boolean active,
            List<CriterionInput> input, Instant now) {
        List<Criterion> next = new ArrayList<>();
        int order = 1;
        // Preliminary criteria first, then technical, keeping the order given within each stage.
        for (CriterionStage stage : CriterionStage.values()) {
            for (CriterionInput c : input) {
                if (c.stage() != stage) {
                    continue;
                }
                next.add(new Criterion(order++, stage, c.name().trim(),
                        c.description() == null || c.description().isBlank() ? null : c.description().trim(),
                        checkedMax(c)));
            }
        }
        if (next.stream().noneMatch(c -> c.getStage() == CriterionStage.PRELIMINARY)) {
            throw new BusinessRuleException("A template needs at least one preliminary (pass/fail) criterion");
        }
        this.name = name;
        this.methodCode = methodCode;
        this.description = description;
        this.active = active;
        this.criteria.clear();
        this.criteria.addAll(next);
        touch(now);
    }

    static BigDecimal checkedMax(CriterionInput c) {
        if (c.stage() == CriterionStage.PRELIMINARY) {
            return null;
        }
        if (c.maxScore() == null || c.maxScore().signum() <= 0) {
            throw new BusinessRuleException("Technical criterion \"" + c.name() + "\" needs a maximum score above 0");
        }
        if (c.maxScore().scale() > 2 || c.maxScore().compareTo(new BigDecimal("9999.99")) > 0) {
            throw new BusinessRuleException("Maximum scores have at most 2 decimals and are below 10,000");
        }
        return c.maxScore();
    }

    public BigDecimal technicalTotal() {
        return criteria.stream().filter(c -> c.getStage() == CriterionStage.TECHNICAL)
                .map(Criterion::getMaxScore).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public String getMethodCode() { return methodCode; }
    public String getDescription() { return description; }
    public boolean isActive() { return active; }
    public List<Criterion> getCriteria() { return List.copyOf(criteria); }
}
