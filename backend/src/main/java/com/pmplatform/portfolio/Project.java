package com.pmplatform.portfolio;

import com.pmplatform.platform.id.UuidV7;
import com.pmplatform.platform.web.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/**
 * The spine of the domain: every procurement, contract, baseline, change and
 * benefit hangs off a project. Money is held as integer minor units (cents) plus
 * an ISO 4217 currency code, never as floating point.
 */
@Entity
@Table(schema = "portfolio", name = "projects")
public class Project implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(nullable = false, updatable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    private String description;

    private String sponsor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProjectPhase phase;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProjectStatus status;

    @Column(name = "budget_minor", nullable = false)
    private long budgetMinor;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    @Transient
    private boolean isNew = true;

    protected Project() {
    }

    public static Project create(UUID tenantId, String code, String name, String description,
            String sponsor, long budgetMinor, String currency, Instant now) {
        Project p = new Project();
        p.id = UuidV7.generate();
        p.tenantId = tenantId;
        p.code = code;
        p.name = name;
        p.description = description;
        p.sponsor = sponsor;
        p.phase = ProjectPhase.INITIATION;
        p.status = ProjectStatus.ACTIVE;
        p.budgetMinor = budgetMinor;
        p.currency = currency;
        p.createdAt = now;
        p.updatedAt = now;
        return p;
    }

    /**
     * Passes the gate that closes the current phase and enters the next one.
     *
     * @return the gate that was passed, for example {@code G1}
     */
    public String advancePhase(Instant now) {
        if (status != ProjectStatus.ACTIVE) {
            throw new BusinessRuleException("Only an active project can pass a gate; this one is "
                    + status);
        }
        String gate = phase.exitGate().orElseThrow(() ->
                new BusinessRuleException("Implementation is the last phase; there is no further gate"));
        phase = phase.next().orElseThrow();
        updatedAt = now;
        return gate;
    }

    /** Closes the project after handover and the defects liability period. */
    public void close(Instant now) {
        if (status != ProjectStatus.ACTIVE) {
            throw new BusinessRuleException("Only an active project can be closed; this one is " + status);
        }
        if (phase != ProjectPhase.IMPLEMENTATION) {
            throw new BusinessRuleException("Pass gate G4 (formal closure) before closing the project");
        }
        status = ProjectStatus.CLOSED;
        updatedAt = now;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }

    @Override
    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getSponsor() { return sponsor; }
    public ProjectPhase getPhase() { return phase; }
    public ProjectStatus getStatus() { return status; }
    public long getBudgetMinor() { return budgetMinor; }
    public String getCurrency() { return currency; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
