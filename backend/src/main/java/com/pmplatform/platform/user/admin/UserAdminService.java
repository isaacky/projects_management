package com.pmplatform.platform.user.admin;

import com.pmplatform.platform.audit.AuditService;
import com.pmplatform.platform.id.UuidV7;
import com.pmplatform.platform.json.JsonText;
import com.pmplatform.platform.user.CurrentUserService;
import com.pmplatform.platform.user.Roles;
import com.pmplatform.platform.user.User;
import com.pmplatform.platform.user.UserRepository;
import com.pmplatform.platform.user.admin.UserAdminDtos.AdminUserView;
import com.pmplatform.platform.user.admin.UserAdminDtos.RoleView;
import com.pmplatform.platform.user.admin.UserAdminDtos.UserRequest;
import com.pmplatform.platform.web.BusinessRuleException;
import com.pmplatform.platform.web.NotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * People and their roles, kept by system administrators. Sign-in itself lives in the identity
 * provider (Keycloak, later connected to Active Directory); a person added here can use the platform
 * once they sign in with the same, verified email address.
 */
@Service
public class UserAdminService {

    private final UserRepository users;
    private final CurrentUserService currentUser;
    private final AuditService audit;
    private final Clock clock;

    public UserAdminService(UserRepository users, CurrentUserService currentUser, AuditService audit, Clock clock) {
        this.users = users;
        this.currentUser = currentUser;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<AdminUserView> list(UUID tenantId) {
        requireAdmin();
        return users.findByTenantIdOrderByDisplayNameAsc(tenantId).stream().map(AdminUserView::from).toList();
    }

    /** The built-in roles first, then any other codes people already hold. */
    @Transactional(readOnly = true)
    public List<RoleView> roles(UUID tenantId) {
        requireAdmin();
        Map<String, Long> holders = users.findByTenantIdOrderByDisplayNameAsc(tenantId).stream()
                .filter(User::isActive)
                .flatMap(u -> u.getRoles().stream())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        List<RoleView> out = new ArrayList<>();
        Roles.CATALOGUE.forEach((code, description) ->
                out.add(new RoleView(code, description, true, holders.getOrDefault(code, 0L))));
        new TreeSet<>(users.roleCodesInUse(tenantId)).stream()
                .filter(code -> !Roles.CATALOGUE.containsKey(code))
                .forEach(code -> out.add(new RoleView(code, "", false, holders.getOrDefault(code, 0L))));
        return out;
    }

    @Transactional
    public AdminUserView create(UUID tenantId, UserRequest req) {
        User me = requireAdmin();
        String email = User.normaliseEmail(req.email());
        if (users.findByTenantIdAndEmail(tenantId, email).isPresent()) {
            throw new BusinessRuleException("Someone with the email " + email + " is already set up.");
        }
        Instant now = Instant.now(clock);
        User u = User.create(UuidV7.generate(), tenantId, email, req.displayName(), req.roles(), now);
        if (Boolean.FALSE.equals(req.active())) {
            u.update(email, req.displayName(), req.roles(), false);
        }
        users.save(u);
        audit.record(tenantId, "user.added", "User", u.getId(), JsonText.object("email", u.getEmail(),
                "name", u.getDisplayName(), "roles", String.join(",", sorted(u.getRoles())),
                "by", me.getEmail()));
        return AdminUserView.from(u);
    }

    @Transactional
    public AdminUserView update(UUID tenantId, UUID userId, UserRequest req) {
        User me = requireAdmin();
        User u = load(tenantId, userId);
        String email = User.normaliseEmail(req.email());
        users.findByTenantIdAndEmail(tenantId, email).filter(other -> !other.getId().equals(userId))
                .ifPresent(other -> {
                    throw new BusinessRuleException("Someone with the email " + email + " is already set up.");
                });
        boolean active = req.active() == null ? u.isActive() : req.active();
        if (u.getId().equals(me.getId())) {
            // Separation of duties: administrators decide who approves what, so they cannot give
            // themselves approval rights, nor lock themselves out.
            if (!active) {
                throw new BusinessRuleException("You cannot deactivate yourself. Ask another administrator.");
            }
            if (!req.roles().contains(Roles.SYSTEM_ADMIN)) {
                throw new BusinessRuleException(
                        "You cannot remove your own administrator role. Ask another administrator.");
            }
            Set<String> added = new TreeSet<>(req.roles());
            added.removeAll(u.getRoles());
            if (!added.isEmpty()) {
                throw new BusinessRuleException("You cannot give yourself roles (" + String.join(", ", added)
                        + "). Ask another administrator.");
            }
        }
        String before = describe(u);
        u.update(email, req.displayName(), req.roles(), active);
        String after = describe(u);
        if (!before.equals(after)) {
            audit.record(tenantId, "user.updated", "User", u.getId(),
                    JsonText.object("before", before, "after", after));
        }
        return AdminUserView.from(u);
    }

    /** For a person whose identity-provider account was replaced: their next sign-in links again by email. */
    @Transactional
    public AdminUserView unlink(UUID tenantId, UUID userId) {
        requireAdmin();
        User u = load(tenantId, userId);
        if (u.getExternalSubject() != null) {
            audit.record(tenantId, "user.signin_unlinked", "User", u.getId(),
                    JsonText.object("email", u.getEmail(), "subject", u.getExternalSubject()));
            u.unlink();
        }
        return AdminUserView.from(u);
    }

    private User requireAdmin() {
        User me = currentUser.require();
        Roles.requireAny(me, Set.of(Roles.SYSTEM_ADMIN), "manage users and roles");
        return me;
    }

    private User load(UUID tenantId, UUID userId) {
        return users.findByTenantIdAndId(tenantId, userId)
                .orElseThrow(() -> new NotFoundException("User " + userId + " not found"));
    }

    private static String describe(User u) {
        return u.getDisplayName() + " <" + u.getEmail() + "> " + (u.isActive() ? "active" : "inactive")
                + " [" + String.join(",", sorted(u.getRoles())) + "]";
    }

    private static List<String> sorted(Set<String> roles) {
        return roles.stream().sorted().toList();
    }
}
