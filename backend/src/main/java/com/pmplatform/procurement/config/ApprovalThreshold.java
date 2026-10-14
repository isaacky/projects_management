package com.pmplatform.procurement.config;

import com.pmplatform.platform.persistence.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One approver step for an amount band. The band includes its minimum and excludes
 * its maximum ([min, max)), so adjacent bands such as 0–5m and 5m–∞ never overlap.
 */
@Entity
@Table(schema = "procurement", name = "approval_thresholds")
public class ApprovalThreshold extends TenantEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApprovalStage stage;

    /** Null = applies to every method. */
    @Column(name = "method_code")
    private String methodCode;

    @Column(name = "min_amount_minor", nullable = false)
    private long minAmountMinor;

    @Column(name = "max_amount_minor")
    private Long maxAmountMinor;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "step_order", nullable = false)
    private int stepOrder;

    @Column(name = "approver_role", nullable = false)
    private String approverRole;

    @Column(nullable = false)
    private boolean active;

    protected ApprovalThreshold() {
    }

    public ApprovalThreshold(UUID tenantId, Instant now) {
        super(tenantId, now);
    }

    public void update(ApprovalStage stage, String methodCode, long minAmountMinor,
            Long maxAmountMinor, String currency, int stepOrder, String approverRole,
            boolean active, Instant now) {
        this.stage = stage;
        this.methodCode = methodCode;
        this.minAmountMinor = minAmountMinor;
        this.maxAmountMinor = maxAmountMinor;
        this.currency = currency;
        this.stepOrder = stepOrder;
        this.approverRole = approverRole;
        this.active = active;
        touch(now);
    }

    public boolean covers(long amountMinor) {
        return amountMinor >= minAmountMinor && (maxAmountMinor == null || amountMinor < maxAmountMinor);
    }

    /** True when the two bands share at least one amount. */
    public boolean overlaps(long otherMin, Long otherMax) {
        long thisMax = maxAmountMinor == null ? Long.MAX_VALUE : maxAmountMinor;
        long thatMax = otherMax == null ? Long.MAX_VALUE : otherMax;
        return minAmountMinor < thatMax && otherMin < thisMax;
    }

    public ApprovalStage getStage() { return stage; }
    public String getMethodCode() { return methodCode; }
    public long getMinAmountMinor() { return minAmountMinor; }
    public Long getMaxAmountMinor() { return maxAmountMinor; }
    public String getCurrency() { return currency; }
    public int getStepOrder() { return stepOrder; }
    public String getApproverRole() { return approverRole; }
    public boolean isActive() { return active; }
}
