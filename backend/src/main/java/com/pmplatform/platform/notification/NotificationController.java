package com.pmplatform.platform.notification;

import com.pmplatform.platform.tenant.TenantContext;
import com.pmplatform.platform.user.CurrentUserService;
import com.pmplatform.platform.user.User;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in user's inbox. */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService service;
    private final CurrentUserService currentUser;

    public NotificationController(NotificationService service, CurrentUserService currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    public record NotificationResponse(UUID id, String type, String title, String body, String link,
            Instant createdAt, Instant readAt) {

        static NotificationResponse from(Notification n) {
            return new NotificationResponse(n.getId(), n.getType(), n.getTitle(), n.getBody(), n.getLink(),
                    n.getCreatedAt(), n.getReadAt());
        }
    }

    public record InboxResponse(long unreadCount, List<NotificationResponse> items) {
    }

    @GetMapping
    public InboxResponse inbox(@RequestParam(defaultValue = "20") int limit) {
        UUID tenant = TenantContext.require();
        User me = currentUser.require();
        return new InboxResponse(service.unreadCount(tenant, me.getId()),
                service.inbox(tenant, me.getId(), limit).stream().map(NotificationResponse::from).toList());
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> read(@PathVariable UUID id) {
        service.markRead(TenantContext.require(), currentUser.require().getId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/read-all")
    public ResponseEntity<Void> readAll() {
        service.markAllRead(TenantContext.require(), currentUser.require().getId());
        return ResponseEntity.noContent().build();
    }
}
