package com.pmplatform.closeout;

import com.pmplatform.closeout.api.CloseoutDtos.LessonRequest;
import com.pmplatform.closeout.api.CloseoutDtos.LessonView;
import com.pmplatform.platform.audit.AuditService;
import com.pmplatform.platform.json.JsonText;
import com.pmplatform.platform.notification.NotificationService;
import com.pmplatform.platform.notification.NotificationService.Message;
import com.pmplatform.platform.user.CurrentUserService;
import com.pmplatform.platform.user.Roles;
import com.pmplatform.platform.user.User;
import com.pmplatform.platform.web.ForbiddenActionException;
import com.pmplatform.platform.web.NotFoundException;
import com.pmplatform.portfolio.Project;
import com.pmplatform.portfolio.ProjectService;
import com.pmplatform.procurement.tender.api.TenderDtos.Ref;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lessons learned. Anyone may record a lesson on a project at any phase; the PMO reviews each one and
 * marks it adopted (with what will change) or noted. The library shows lessons across all projects.
 */
@Service
public class LessonService {

    private static final int LIBRARY_LIMIT = 1000;

    private final LessonRepository lessons;
    private final ProjectService projects;
    private final CurrentUserService currentUser;
    private final NotificationService notifications;
    private final AuditService audit;
    private final Clock clock;

    public LessonService(LessonRepository lessons, ProjectService projects, CurrentUserService currentUser,
            NotificationService notifications, AuditService audit, Clock clock) {
        this.lessons = lessons;
        this.projects = projects;
        this.currentUser = currentUser;
        this.notifications = notifications;
        this.audit = audit;
        this.clock = clock;
    }

    /** All lessons, newest first, optionally filtered by text, category or kind. */
    @Transactional(readOnly = true)
    public List<LessonView> library(UUID tenantId, String q, LessonCategory category, LessonKind kind) {
        User me = currentUser.require();
        String needle = q == null || q.isBlank() ? null : q.trim().toLowerCase(Locale.ROOT);
        List<Lesson> rows = lessons.findByTenantIdOrderByCreatedAtDesc(tenantId, PageRequest.of(0, LIBRARY_LIMIT))
                .stream()
                .filter(l -> category == null || l.getCategory() == category)
                .filter(l -> kind == null || l.getKind() == kind)
                .filter(l -> needle == null || contains(l, needle))
                .toList();
        return views(tenantId, rows, me);
    }

    @Transactional(readOnly = true)
    public List<LessonView> forProject(UUID tenantId, UUID projectId, User me) {
        return views(tenantId, lessons.findByTenantIdAndProjectIdOrderByCreatedAtDesc(tenantId, projectId), me);
    }

    @Transactional
    public LessonView add(UUID tenantId, UUID projectId, LessonRequest req) {
        User me = currentUser.require();
        Project p = projects.get(tenantId, projectId);
        Instant now = Instant.now(clock);
        Lesson l = new Lesson(tenantId, projectId, me.getId(), now);
        l.write(req.kind(), req.category(), req.phase() != null ? req.phase() : p.getPhase(), req.title(),
                req.whatHappened(), req.recommendation(), now);
        lessons.save(l);
        audit.record(tenantId, "lesson.recorded", "Project", projectId,
                JsonText.object("title", l.getTitle(), "kind", l.getKind(), "category", l.getCategory()));
        notifications.notifyRole(tenantId, Roles.PMO_LEAD, new Message("lesson.recorded",
                cap("Lesson learned on " + p.getCode() + ": " + l.getTitle(), 200),
                cap(l.getWhatHappened(), 1000), "/lessons"));
        return views(tenantId, List.of(l), me).getFirst();
    }

    @Transactional
    public LessonView edit(UUID tenantId, UUID projectId, UUID lessonId, LessonRequest req) {
        User me = currentUser.require();
        Lesson l = load(tenantId, projectId, lessonId);
        if (!canEdit(l, me)) {
            throw new ForbiddenActionException("Whoever recorded the lesson, the project manager or the PMO edits it");
        }
        l.write(req.kind(), req.category(), req.phase() != null ? req.phase() : l.getPhase(), req.title(),
                req.whatHappened(), req.recommendation(), Instant.now(clock));
        audit.record(tenantId, "lesson.edited", "Project", projectId, JsonText.object("title", l.getTitle()));
        return views(tenantId, List.of(l), me).getFirst();
    }

    @Transactional
    public LessonView review(UUID tenantId, UUID lessonId, LessonStatus status, String note) {
        User me = currentUser.require();
        Roles.requireAny(me, Set.of(Roles.PMO_LEAD), "review lessons learned");
        Lesson l = lessons.findByTenantIdAndId(tenantId, lessonId)
                .orElseThrow(() -> new NotFoundException("Lesson not found"));
        l.review(status, me.getId(), note, Instant.now(clock));
        audit.record(tenantId, "lesson.reviewed", "Project", l.getProjectId(),
                JsonText.object("title", l.getTitle(), "status", status, "note", l.getReviewNote()));
        if (!l.getCreatedBy().equals(me.getId())) {
            notifications.notifyUsers(tenantId, List.of(l.getCreatedBy()), new Message("lesson.reviewed",
                    cap("Your lesson \"" + l.getTitle() + "\" was " + status.name().toLowerCase(), 200),
                    l.getReviewNote() == null ? "No note." : cap(l.getReviewNote(), 1000), "/lessons"));
        }
        return views(tenantId, List.of(l), me).getFirst();
    }

    private Lesson load(UUID tenantId, UUID projectId, UUID lessonId) {
        return lessons.findByTenantIdAndId(tenantId, lessonId).filter(l -> l.getProjectId().equals(projectId))
                .orElseThrow(() -> new NotFoundException("Lesson not found"));
    }

    private static boolean canEdit(Lesson l, User me) {
        return l.getStatus() == LessonStatus.SUBMITTED
                && (me.getId().equals(l.getCreatedBy()) || me.getRoles().stream().anyMatch(Roles.PLAN_EDITORS::contains));
    }

    private static boolean contains(Lesson l, String needle) {
        return l.getTitle().toLowerCase(Locale.ROOT).contains(needle)
                || l.getWhatHappened().toLowerCase(Locale.ROOT).contains(needle)
                || (l.getRecommendation() != null && l.getRecommendation().toLowerCase(Locale.ROOT).contains(needle));
    }

    private List<LessonView> views(UUID tenantId, List<Lesson> rows, User me) {
        Map<UUID, Project> projectMap = projects.byIds(tenantId,
                rows.stream().map(Lesson::getProjectId).collect(Collectors.toSet()));
        Set<UUID> ids = new HashSet<>();
        rows.forEach(l -> {
            ids.add(l.getCreatedBy());
            if (l.getReviewedBy() != null) {
                ids.add(l.getReviewedBy());
            }
        });
        Map<UUID, User> people = ids.isEmpty() ? Map.of() : currentUser.byIds(tenantId, ids).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        boolean pmo = me.hasRole(Roles.PMO_LEAD);
        return rows.stream().map(l -> {
            Project p = projectMap.get(l.getProjectId());
            return new LessonView(l.getId(), l.getProjectId(), p == null ? "?" : p.getCode(),
                    p == null ? "Unknown project" : p.getName(), l.getKind(), l.getCategory(), l.getPhase(),
                    l.getTitle(), l.getWhatHappened(), l.getRecommendation(), l.getStatus(), l.getReviewNote(),
                    ref(people, l.getReviewedBy()), l.getReviewedAt(), ref(people, l.getCreatedBy()), l.getCreatedAt(),
                    canEdit(l, me), pmo && l.getStatus() == LessonStatus.SUBMITTED, l.getVersion());
        }).toList();
    }

    static Ref ref(Map<UUID, User> people, UUID id) {
        if (id == null) {
            return null;
        }
        User u = people.get(id);
        return new Ref(id, u == null ? "Unknown user" : u.getDisplayName());
    }

    static String cap(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
