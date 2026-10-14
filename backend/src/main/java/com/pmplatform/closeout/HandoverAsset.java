package com.pmplatform.closeout;

import com.pmplatform.platform.persistence.TenantEntity;
import com.pmplatform.platform.web.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** An asset handed over to the operating unit (for its asset register). */
@Entity
@Table(schema = "closeout", name = "handover_assets")
public class HandoverAsset extends TenantEntity {

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private BigDecimal quantity;

    @Column(nullable = false)
    private String unit;

    private String tag;

    private String location;

    @Column(name = "value_minor")
    private Long valueMinor;

    protected HandoverAsset() {
    }

    public HandoverAsset(UUID tenantId, UUID projectId, int sortOrder, String description, BigDecimal quantity,
            String unit, String tag, String location, Long valueMinor, Instant now) {
        super(tenantId, now);
        if (quantity == null || quantity.signum() <= 0) {
            throw new BusinessRuleException("The quantity must be more than zero");
        }
        if (valueMinor != null && valueMinor < 0) {
            throw new BusinessRuleException("The value cannot be negative");
        }
        this.projectId = projectId;
        this.sortOrder = sortOrder;
        this.description = Text.required(description, 300, "The asset description");
        this.quantity = quantity;
        this.unit = Text.required(unit, 32, "The unit");
        this.tag = Text.optional(tag, 120, "The tag or serial numbers");
        this.location = Text.optional(location, 200, "The location");
        this.valueMinor = valueMinor;
    }

    public UUID getProjectId() { return projectId; }
    public int getSortOrder() { return sortOrder; }
    public String getDescription() { return description; }
    public BigDecimal getQuantity() { return quantity; }
    public String getUnit() { return unit; }
    public String getTag() { return tag; }
    public String getLocation() { return location; }
    public Long getValueMinor() { return valueMinor; }
}
