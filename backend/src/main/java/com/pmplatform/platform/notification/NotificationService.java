package com.pmplatform.platform.notification;

import com.pmplatform.platform.user.CurrentUserService;
import com.pmplatform.platform.user.User;
import com.pmplatform.platform.web.NotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The in-app inbox. Messages are written in the same transaction as the change that
 * caused them, so nobody is told about an approval that was then rolled back.
 * (Email delivery can later read from this table.)
 */
@Service
public class NotificationService {

    private final NotificationRepository repository;
    private final CurrentUserService users;
    private final Clock clock;

    public NotificationService(NotificationRepository repository, CurrentUserService users, Clock clock) {
        this.repository = repository;
        this.users = users;
        this.clock = clock;
    }

    public record Message(String type, String title, String body, String link) {
    }

    @Transactional
    public void notifyUsers(UUID tenantId, Collection<UUID> recipientIds, Message m) {
        Instant now = Instant.now(clock);
        for (UUID recipient : recipientIds) {
            repository.save(new Notification(tenantId, recipient, m.type(), cap(m.title(), 200), cap(m.body(), 1000),
                    m.link(), now));
        }
    }

    /** Notifications are short; longer text stays on the page the link opens. */
    static String cap(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max - 1) + "\u2026";
    }

    /** Sends to every active user holding the role; returns how many were notified. */
    @Transactional
    public int notifyRole(UUID tenantId, String role, Message m) {
        List<UUID> ids = users.withRole(tenantId, role).stream().map(User::getId).toList();
        notifyUsers(tenantId, ids, m);
        return ids.size();
    }

    @Transactional(readOnly = true)
    public List<Notification> inbox(UUID tenantId, UUID userId, int limit) {
        return repository.findByTenantIdAndRecipientIdOrderByCreatedAtDesc(tenantId, userId,
                PageRequest.of(0, Math.min(Math.max(limit, 1), 100)));
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID tenantId, UUID userId) {
        return repository.countByTenantIdAndRecipientIdAndReadAtIsNull(tenantId, userId);
    }

    @Transactional
    public void markRead(UUID tenantId, UUID userId, UUID id) {
        repository.findByTenantIdAndRecipientIdAndId(tenantId, userId, id)
                .orElseThrow(() -> new NotFoundException("Notification not found"))
                .markRead(Instant.now(clock));
    }

    @Transactional
    public int markAllRead(UUID tenantId, UUID userId) {
        return repository.markAllRead(tenantId, userId, Instant.now(clock));
    }
}
