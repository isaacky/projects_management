package com.pmplatform.platform.user;

import com.pmplatform.platform.audit.AuditService;
import com.pmplatform.platform.json.JsonText;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Links a person's first sign-in to the record an administrator created for them: the token's
 * verified email finds the person, and the token's subject is stored on it. Later sign-ins match on
 * the subject alone, so changing the email in the identity provider afterwards does not move the
 * account. Runs in its own transaction because callers are often read-only.
 */
@Component
class UserLinker {

    private static final Logger LOG = LoggerFactory.getLogger(UserLinker.class);

    private final UserRepository users;
    private final ObjectProvider<AuditService> audit; // lazy: the audit service itself needs the current user

    UserLinker(UserRepository users, ObjectProvider<AuditService> audit) {
        this.users = users;
        this.audit = audit;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<User> link(UUID tenantId, String subject, String verifiedEmail) {
        if (verifiedEmail == null || verifiedEmail.isBlank()) {
            return Optional.empty();
        }
        if (users.linkSubject(tenantId, verifiedEmail.trim(), subject) == 1) {
            Optional<User> linked = users.findByTenantIdAndExternalSubject(tenantId, subject);
            linked.ifPresent(u -> {
                LOG.info("Linked sign-in {} to user {}", subject, u.getId());
                audit.getObject().record(tenantId, "user.signin_linked", "User", u.getId(),
                        JsonText.object("email", u.getEmail(), "subject", subject));
            });
            return linked;
        }
        return users.findByTenantIdAndExternalSubject(tenantId, subject); // linked by a parallel request
    }
}
