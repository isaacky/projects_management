package com.pmplatform.platform.user;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** A person in a tenant, with the roles that decide what they may approve. */
@Entity
@Table(schema = "platform", name = "users")
public class User {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "external_subject")
    private String externalSubject;

    @Column(nullable = false)
    private String email;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(schema = "platform", name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role_code")
    private Set<String> roles = new HashSet<>();

    protected User() {
    }

    /** A new person, added by an administrator (or the start-up bootstrap). Not linked to a login yet. */
    public static User create(UUID id, UUID tenantId, String email, String displayName, Set<String> roles,
            Instant now) {
        User u = new User();
        u.id = id;
        u.tenantId = tenantId;
        u.email = normaliseEmail(email);
        u.displayName = displayName.trim();
        u.active = true;
        u.createdAt = now;
        u.roles = new HashSet<>(roles);
        return u;
    }

    /** Emails are compared without case; stored trimmed and in lower case. */
    public static String normaliseEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(java.util.Locale.ROOT);
    }

    public void update(String email, String displayName, Set<String> roles, boolean active) {
        this.email = normaliseEmail(email);
        this.displayName = displayName.trim();
        this.roles.clear();
        this.roles.addAll(roles);
        this.active = active;
    }

    public void addRole(String role) {
        roles.add(role);
    }

    /** Forgets the identity-provider account, so the next sign-in with this email links afresh. */
    public void unlink() {
        this.externalSubject = null;
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getExternalSubject() { return externalSubject; }
    public String getEmail() { return email; }
    public String getDisplayName() { return displayName; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Set<String> getRoles() { return Set.copyOf(roles); }
}
