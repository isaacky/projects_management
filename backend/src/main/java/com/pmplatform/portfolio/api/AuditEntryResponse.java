package com.pmplatform.portfolio.api;

import com.pmplatform.platform.audit.AuditEvent;
import java.time.Instant;

public record AuditEntryResponse(Instant occurredAt, String actor, String action, String payload,
        String hash) {

    public static AuditEntryResponse from(AuditEvent e) {
        return new AuditEntryResponse(e.getOccurredAt(), e.getActor(), e.getAction(), e.getPayload(),
                e.getHash());
    }
}
