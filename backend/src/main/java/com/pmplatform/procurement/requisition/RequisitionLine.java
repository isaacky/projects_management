package com.pmplatform.procurement.requisition;

import com.pmplatform.platform.id.UuidV7;
import com.pmplatform.platform.web.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/** One item on a requisition. Line total = quantity × unit price, rounded to the cent. */
@Entity
@Table(schema = "procurement", name = "requisition_lines")
public class RequisitionLine {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requisition_id", nullable = false, updatable = false)
    private Requisition requisition;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal quantity;

    @Column(nullable = false)
    private String unit;

    @Column(name = "unit_price_minor", nullable = false)
    private long unitPriceMinor;

    @Column(name = "total_minor", nullable = false)
    private long totalMinor;

    /** Null until saved, which is how Hibernate tells new lines from existing ones. */
    @Version
    private Long version;

    protected RequisitionLine() {
    }

    RequisitionLine(Requisition requisition, int lineNo, String description, BigDecimal quantity,
            String unit, long unitPriceMinor) {
        if (quantity == null || quantity.signum() <= 0) {
            throw new BusinessRuleException("Line " + lineNo + ": quantity must be more than zero");
        }
        if (unitPriceMinor < 0) {
            throw new BusinessRuleException("Line " + lineNo + ": unit price cannot be negative");
        }
        this.id = UuidV7.generate();
        this.requisition = requisition;
        this.lineNo = lineNo;
        this.description = description;
        this.quantity = quantity.setScale(3, RoundingMode.HALF_UP);
        this.unit = unit;
        this.unitPriceMinor = unitPriceMinor;
        this.totalMinor = lineTotal(this.quantity, unitPriceMinor);
    }

    static long lineTotal(BigDecimal quantity, long unitPriceMinor) {
        try {
            return quantity.multiply(BigDecimal.valueOf(unitPriceMinor))
                    .setScale(0, RoundingMode.HALF_UP)
                    .longValueExact();
        } catch (ArithmeticException e) {
            throw new BusinessRuleException("Line total is too large");
        }
    }

    public UUID getId() { return id; }
    public int getLineNo() { return lineNo; }
    public String getDescription() { return description; }
    public BigDecimal getQuantity() { return quantity; }
    public String getUnit() { return unit; }
    public long getUnitPriceMinor() { return unitPriceMinor; }
    public long getTotalMinor() { return totalMinor; }
}
