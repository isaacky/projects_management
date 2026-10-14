package com.pmplatform.procurement.config;

import com.pmplatform.platform.persistence.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A procurement method a tenant uses, and the estimated-value range it applies to. */
@Entity
@Table(schema = "procurement", name = "methods")
public class ProcurementMethod extends TenantEntity {

    @Column(nullable = false, updatable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "min_value_minor")
    private Long minValueMinor;

    @Column(name = "max_value_minor")
    private Long maxValueMinor;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "min_bid_period_days", nullable = false)
    private int minBidPeriodDays;

    @Column(name = "requires_public_advert", nullable = false)
    private boolean requiresPublicAdvert;

    @Column(name = "requires_justification", nullable = false)
    private boolean requiresJustification;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /** Days between the award notice and the earliest contract signature. */
    @Column(name = "standstill_days", nullable = false)
    private int standstillDays = 14;

    protected ProcurementMethod() {
    }

    public ProcurementMethod(UUID tenantId, String code, Instant now) {
        super(tenantId, now);
        this.code = code;
    }

    public void update(String name, String description, boolean enabled, Long minValueMinor,
            Long maxValueMinor, String currency, int minBidPeriodDays, boolean requiresPublicAdvert,
            boolean requiresJustification, int sortOrder, Instant now) {
        this.name = name;
        this.description = description;
        this.enabled = enabled;
        this.minValueMinor = minValueMinor;
        this.maxValueMinor = maxValueMinor;
        this.currency = currency;
        this.minBidPeriodDays = minBidPeriodDays;
        this.requiresPublicAdvert = requiresPublicAdvert;
        this.requiresJustification = requiresJustification;
        this.sortOrder = sortOrder;
        touch(now);
    }

    /** Whether this method may be used for a purchase of the given estimated value. */
    public boolean covers(long amountMinor, String amountCurrency) {
        return currency.equals(amountCurrency)
                && (minValueMinor == null || amountMinor >= minValueMinor)
                && (maxValueMinor == null || amountMinor <= maxValueMinor);
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public boolean isEnabled() { return enabled; }
    public Long getMinValueMinor() { return minValueMinor; }
    public Long getMaxValueMinor() { return maxValueMinor; }
    public String getCurrency() { return currency; }
    public int getMinBidPeriodDays() { return minBidPeriodDays; }
    public boolean isRequiresPublicAdvert() { return requiresPublicAdvert; }
    public boolean isRequiresJustification() { return requiresJustification; }
    public int getSortOrder() { return sortOrder; }
    public int getStandstillDays() { return standstillDays; }

    public void setStandstillDays(int days, Instant now) {
        this.standstillDays = days;
        touch(now);
    }
}
