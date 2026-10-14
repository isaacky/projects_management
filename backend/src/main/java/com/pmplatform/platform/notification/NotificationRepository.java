package com.pmplatform.platform.notification;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findByTenantIdAndRecipientIdOrderByCreatedAtDesc(UUID tenantId, UUID recipientId,
            Pageable page);

    long countByTenantIdAndRecipientIdAndReadAtIsNull(UUID tenantId, UUID recipientId);

    Optional<Notification> findByTenantIdAndRecipientIdAndId(UUID tenantId, UUID recipientId, UUID id);

    @Modifying
    @Query("update Notification n set n.readAt = :now "
            + "where n.tenantId = :tenantId and n.recipientId = :recipientId and n.readAt is null")
    int markAllRead(@Param("tenantId") UUID tenantId, @Param("recipientId") UUID recipientId,
            @Param("now") Instant now);
}
