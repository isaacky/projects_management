package com.pmplatform.platform.user;

import com.pmplatform.platform.tenant.TenantContext;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves the signed-in user for the current request.
 *
 * <ul>
 *   <li>Production: the user whose {@code external_subject} matches the JWT subject. On a person's
 *       first sign-in nobody matches yet; then the active, unlinked user with the token's verified
 *       email is linked to the subject ({@link UserLinker}).</li>
 *   <li>Development: the {@code X-Dev-User-ID} header ("Viewing as" switcher), falling
 *       back to {@code pmp.dev.default-user-id}.</li>
 * </ul>
 */
@Service
public class CurrentUserService {

    private final UserRepository users;
    private final UserLinker linker;
    private final UUID devDefaultUser;

    public CurrentUserService(UserRepository users, UserLinker linker,
            @Value("${pmp.tenant.allow-header:false}") boolean devMode,
            @Value("${pmp.dev.default-user-id:}") String devDefaultUser) {
        this.users = users;
        this.linker = linker;
        this.devDefaultUser = devMode && !devDefaultUser.isBlank() ? UUID.fromString(devDefaultUser) : null;
    }

    @Transactional(readOnly = true)
    public Optional<User> current() {
        UUID tenant = TenantContext.current().orElse(null);
        if (tenant == null) {
            return Optional.empty();
        }
        String subject = UserContext.jwtSubject();
        if (subject != null) {
            Optional<User> user = users.findByTenantIdAndExternalSubject(tenant, subject);
            String email = UserContext.verifiedEmail();
            // Link at most once per request, and only when there is someone to link to (so a person
            // who is not set up costs one query, not a second connection, per request).
            if (user.isEmpty() && email != null && UserContext.firstLinkAttempt()
                    && users.existsUnlinked(tenant, email.trim())) {
                user = linker.link(tenant, subject, email);
            }
            return user.filter(User::isActive);
        }
        UUID devUser = UserContext.devUserId() != null ? UserContext.devUserId() : devDefaultUser;
        return devUser == null ? Optional.empty()
                : users.findByTenantIdAndId(tenant, devUser).filter(User::isActive);
    }

    public User require() {
        return current().orElseThrow(UnknownUserException::new);
    }

    @Transactional(readOnly = true)
    public List<User> withRole(UUID tenantId, String role) {
        return users.findActiveWithRole(tenantId, role);
    }

    @Transactional(readOnly = true)
    public List<User> all(UUID tenantId) {
        return users.findByTenantIdAndActiveTrueOrderByDisplayNameAsc(tenantId);
    }

    @Transactional(readOnly = true)
    public List<User> byIds(UUID tenantId, java.util.Collection<UUID> ids) {
        return ids.isEmpty() ? List.of() : users.findByTenantIdAndIdIn(tenantId, ids);
    }
}
