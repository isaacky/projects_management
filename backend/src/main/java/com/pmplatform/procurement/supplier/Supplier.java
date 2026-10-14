package com.pmplatform.procurement.supplier;

import com.pmplatform.platform.persistence.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A supplier in the organisation's register, identified by its KRA PIN. */
@Entity
@Table(schema = "procurement", name = "suppliers")
public class Supplier extends TenantEntity {

    @Column(nullable = false)
    private String name;

    @Column(name = "kra_pin", nullable = false, updatable = false)
    private String kraPin;

    @Column(name = "registration_no")
    private String registrationNo;

    private String email;
    private String phone;
    private String address;
    private String categories;

    @Enumerated(EnumType.STRING)
    @Column(name = "agpo_category")
    private AgpoCategory agpoCategory;

    @Column(name = "agpo_certificate_no")
    private String agpoCertificateNo;

    @Column(name = "agpo_expiry")
    private LocalDate agpoExpiry;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SupplierStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Supplier() {
    }

    public Supplier(UUID tenantId, String kraPin, Instant now) {
        super(tenantId, now);
        this.kraPin = kraPin;
        this.status = SupplierStatus.ACTIVE;
        this.createdAt = now;
    }

    public void update(String name, String registrationNo, String email, String phone, String address,
            String categories, AgpoCategory agpoCategory, String agpoCertificateNo, LocalDate agpoExpiry,
            SupplierStatus status, Instant now) {
        this.name = name;
        this.registrationNo = registrationNo;
        this.email = email;
        this.phone = phone;
        this.address = address;
        this.categories = categories;
        this.agpoCategory = agpoCategory;
        this.agpoCertificateNo = agpoCertificateNo;
        this.agpoExpiry = agpoExpiry;
        this.status = status;
        touch(now);
    }

    public boolean mayBid() {
        return status == SupplierStatus.ACTIVE;
    }

    /** Holds an AGPO category whose certificate has not expired on {@code day}. */
    public boolean agpoValidOn(LocalDate day) {
        return agpoCategory != null && (agpoExpiry == null || !agpoExpiry.isBefore(day));
    }

    public String getName() { return name; }
    public String getKraPin() { return kraPin; }
    public String getRegistrationNo() { return registrationNo; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getAddress() { return address; }
    public String getCategories() { return categories; }
    public AgpoCategory getAgpoCategory() { return agpoCategory; }
    public String getAgpoCertificateNo() { return agpoCertificateNo; }
    public LocalDate getAgpoExpiry() { return agpoExpiry; }
    public SupplierStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
