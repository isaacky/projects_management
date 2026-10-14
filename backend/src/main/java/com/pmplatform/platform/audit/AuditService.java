package com.pmplatform.platform.audit;

import com.pmplatform.platform.id.UuidV7;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import com.pmplatform.platform.user.CurrentUserService;
import com.pmplatform.platform.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Appends hash-chained audit events. Must be called inside the business
 * transaction so the audit entry commits or rolls back with the change it records.
 */
@Service
public class AuditService {

    private final AuditEventRepository repository;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final CurrentUserService currentUser;

    public AuditService(AuditEventRepository repository, JdbcTemplate jdbc, Clock clock,
            CurrentUserService currentUser) {
        this.repository = repository;
        this.jdbc = jdbc;
        this.clock = clock;
        this.currentUser = currentUser;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public AuditEvent record(UUID tenantId, String action, String entityType, UUID entityId,
            String payloadJson) {
        // Serialise writers per tenant so two events never share a predecessor.
        jdbc.queryForList("select pg_advisory_xact_lock(hashtext(?))", tenantId.toString());

        String prevHash = repository.findFirstByTenantIdOrderBySeqDesc(tenantId)
                .map(AuditEvent::getHash)
                .orElse(null);
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        String actor = currentActor();
        String payload = payloadJson == null ? "{}" : payloadJson;
        String hash = AuditHasher.hash(prevHash, tenantId, now, actor, action, entityType, entityId,
                payload);

        return repository.save(new AuditEvent(UuidV7.generate(), tenantId, now, actor, action,
                entityType, entityId, payload, prevHash, hash));
    }

    @Transactional(readOnly = true)
    public List<AuditEvent> history(UUID tenantId, UUID entityId) {
        return repository.findByTenantIdAndEntityIdOrderBySeqAsc(tenantId, entityId);
    }

    /** "Display Name <email>" of the signed-in user, or "system" for background work. */
    private String currentActor() {
        return currentUser.current()
                .map((User u) -> u.getDisplayName() + " <" + u.getEmail() + ">")
                .orElse("system");
    }
}
