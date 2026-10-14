package com.pmplatform.platform.audit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

/** SHA-256 over the previous hash and the event's fields, in a fixed order. */
public final class AuditHasher {

    private AuditHasher() {
    }

    public static String hash(String prevHash, UUID tenantId, Instant occurredAt, String actor,
            String action, String entityType, UUID entityId, String payload) {
        String material = String.join("|",
                prevHash == null ? "" : prevHash,
                tenantId.toString(),
                occurredAt.toString(),
                actor,
                action,
                entityType,
                entityId.toString(),
                payload);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(material.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
