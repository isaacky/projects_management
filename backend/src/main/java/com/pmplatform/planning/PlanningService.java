package com.pmplatform.planning;

import com.pmplatform.planning.MsProjectImport.ImportedLink;
import com.pmplatform.planning.MsProjectImport.ImportedTask;
import com.pmplatform.planning.MsProjectImport.Parsed;
import com.pmplatform.planning.ScheduleEngine.Result;
import com.pmplatform.planning.ScheduleEngine.Task;
import com.pmplatform.planning.ScheduleEngine.Timing;
import com.pmplatform.planning.api.PlanningDtos.ActivityInput;
import com.pmplatform.planning.api.PlanningDtos.ActivityView;
import com.pmplatform.planning.api.PlanningDtos.CurvePoint;
import com.pmplatform.planning.api.PlanningDtos.Metrics;
import com.pmplatform.planning.api.PlanningDtos.Permissions;
import com.pmplatform.planning.api.PlanningDtos.PredecessorInput;
import com.pmplatform.planning.api.PlanningDtos.PredecessorView;
import com.pmplatform.planning.api.PlanningDtos.ProgressRequest;
import com.pmplatform.planning.api.PlanningDtos.SaveScheduleRequest;
import com.pmplatform.planning.api.PlanningDtos.ScheduleView;
import com.pmplatform.platform.audit.AuditService;
import com.pmplatform.platform.json.JsonText;
import com.pmplatform.platform.user.CurrentUserService;
import com.pmplatform.platform.user.Roles;
import com.pmplatform.platform.user.User;
import com.pmplatform.platform.web.BusinessRuleException;
import com.pmplatform.platform.web.ForbiddenActionException;
import com.pmplatform.platform.web.NotFoundException;
import com.pmplatform.portfolio.ProjectService;
import com.pmplatform.procurement.tender.api.TenderDtos.Ref;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Project schedules: the activity outline and dependencies, the baseline, progress and the S-curve.
 * Project managers and the PMO edit plans; activity owners may also record progress on their
 * activities. Re-baselining needs the PMO lead and a reason.
 */
@Service
public class PlanningService {

    static final ZoneId LOCAL_ZONE = ZoneId.of("Africa/Nairobi");
    /** Largest MS Project XML file accepted (the 3000-task limit is far below this). */
    static final long MAX_IMPORT_BYTES = 10L * 1024 * 1024;

    private final ScheduleRepository schedules;
    private final ActivityRepository activities;
    private final DependencyRepository dependencies;
    private final ProgressSnapshotRepository snapshots;
    private final ProjectService projects;
    private final CurrentUserService currentUser;
    private final AuditService audit;
    private final Clock clock;

    public PlanningService(ScheduleRepository schedules, ActivityRepository activities,
            DependencyRepository dependencies, ProgressSnapshotRepository snapshots, ProjectService projects,
            CurrentUserService currentUser, AuditService audit, Clock clock) {
        this.schedules = schedules;
        this.activities = activities;
        this.dependencies = dependencies;
        this.snapshots = snapshots;
        this.projects = projects;
        this.currentUser = currentUser;
        this.audit = audit;
        this.clock = clock;
    }

    /** Schedule figures for other contexts (status reports, portfolio health). Empty without activities. */
    @Transactional(readOnly = true)
    public Optional<Result> compute(UUID tenantId, UUID projectId) {
        Optional<Schedule> s = schedules.findByTenantIdAndProjectId(tenantId, projectId);
        if (s.isEmpty()) {
            return Optional.empty();
        }
        List<Activity> acts = activities.findByTenantIdAndProjectId(tenantId, projectId);
        if (acts.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(run(s.get(), acts, dependencies.findByProjectId(projectId)));
    }

    @Transactional(readOnly = true)
    public ScheduleView view(UUID tenantId, UUID projectId) {
        projects.get(tenantId, projectId);
        User me = currentUser.require();
        Optional<Schedule> s = schedules.findByTenantIdAndProjectId(tenantId, projectId);
        if (s.isEmpty()) {
            boolean edit = isEditor(me);
            return new ScheduleView(false, projectId, null, null, null, null, 0, 0, List.of(), null, List.of(),
                    List.of(), List.of(), new Permissions(edit, false, edit));
        }
        return view(s.get(), me, List.of());
    }

    // ================================================================== editing

    @Transactional
    public ScheduleView save(UUID tenantId, UUID projectId, SaveScheduleRequest req) {
        User me = requireEditor("edit schedules");
        projects.get(tenantId, projectId);
        Instant now = now();
        Schedule s = schedules.lock(tenantId, projectId).orElse(null);
        if (s == null) {
            s = schedules.save(new Schedule(tenantId, projectId, req.startDate(), now));
        } else {
            if (req.version() == null || req.version() != s.getVersion()) {
                throw new BusinessRuleException("Someone else changed this schedule; reload it and make your changes again");
            }
            s.setStartDate(req.startDate(), now);
        }
        checkOwners(tenantId, req.activities().stream().map(ActivityInput::ownerId).toList());
        Map<UUID, Activity> existing = activities.findByTenantIdAndProjectId(tenantId, projectId).stream()
                .collect(Collectors.toMap(Activity::getId, Function.identity()));
        dependencies.deleteAll(dependencies.findByProjectId(projectId));
        dependencies.flush();

        Map<String, Activity> byKey = new HashMap<>();
        Map<String, Integer> nextOrder = new HashMap<>();
        Set<UUID> kept = new HashSet<>();
        for (ActivityInput in : req.activities()) {
            if (byKey.containsKey(in.key())) {
                throw new BusinessRuleException("Two rows share the key " + in.key());
            }
            Activity a = existingFor(in.key(), existing);
            if (a == null) {
                a = new Activity(tenantId, projectId, now);
            } else {
                kept.add(a.getId());
            }
            UUID parentId = null;
            if (in.parentKey() != null && !in.parentKey().isBlank()) {
                Activity parent = byKey.get(in.parentKey());
                if (parent == null) {
                    throw new BusinessRuleException("\"" + in.name() + "\": its parent must come before it in the list");
                }
                parentId = parent.getId();
            }
            String siblingKey = parentId == null ? "" : parentId.toString();
            int order = nextOrder.merge(siblingKey, 1, Integer::sum);
            a.define(parentId, order, in.name(), in.description(), in.durationDays(), in.earliestStart(), in.weight(),
                    in.ownerId(), now);
            byKey.put(in.key(), a);
            activities.save(a);
        }
        // Summaries carry no duration or progress of their own.
        Set<UUID> parents = byKey.values().stream().map(Activity::getParentId).filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        for (Activity a : byKey.values()) {
            if (parents.contains(a.getId())) {
                if (a.hasProgress()) {
                    throw new BusinessRuleException("\"" + a.getName() + "\" has progress recorded and cannot become a "
                            + "summary row");
                }
                if (a.getDurationDays() != 0) {
                    a.define(a.getParentId(), a.getSortOrder(), a.getName(), a.getDescription(), 0, a.getEarliestStart(),
                            a.getWeight(), a.getOwnerId(), now);
                }
            }
        }
        activities.flush();

        List<Activity> removed = existing.values().stream().filter(a -> !kept.contains(a.getId())).toList();
        for (Activity a : removed) {
            if (a.hasProgress()) {
                throw new BusinessRuleException("\"" + a.getName() + "\" has progress recorded and cannot be removed");
            }
        }
        deleteDeepestFirst(removed);
        activities.flush();

        List<Dependency> links = new ArrayList<>();
        Set<String> seenLinks = new HashSet<>();
        for (ActivityInput in : req.activities()) {
            Activity succ = byKey.get(in.key());
            for (PredecessorInput p : in.predecessors() == null ? List.<PredecessorInput>of() : in.predecessors()) {
                Activity pred = byKey.get(p.key());
                if (pred == null) {
                    throw new BusinessRuleException("\"" + in.name() + "\" depends on a row that is not in the schedule");
                }
                if (pred.getId().equals(succ.getId())) {
                    throw new BusinessRuleException("\"" + in.name() + "\" cannot depend on itself");
                }
                if (seenLinks.add(pred.getId() + ">" + succ.getId())) {
                    links.add(new Dependency(projectId, pred.getId(), succ.getId(), p.type(),
                            p.lagDays() == null ? 0 : p.lagDays()));
                }
            }
        }
        List<Activity> all = new ArrayList<>(byKey.values());
        run(s, all, links); // validates outline and loops before anything is committed
        dependencies.saveAll(links);
        s.changed(now);
        audit.record(tenantId, "schedule.saved", "Project", projectId,
                JsonText.object("activities", all.size(), "dependencies", links.size(), "removed", removed.size(),
                        "startDate", req.startDate()));
        return view(s, me, List.of());
    }

    @Transactional
    public ScheduleView baseline(UUID tenantId, UUID projectId, String note) {
        User me = requireEditor("baseline schedules");
        Schedule s = schedules.lock(tenantId, projectId)
                .orElseThrow(() -> new BusinessRuleException("Build the schedule before baselining it"));
        if (s.getBaselineCount() > 0) {
            Roles.requireAny(me, Set.of(Roles.PMO_LEAD), "re-baseline a schedule");
        }
        List<Activity> acts = activities.findByTenantIdAndProjectId(tenantId, projectId);
        if (acts.isEmpty()) {
            throw new BusinessRuleException("Build the schedule before baselining it");
        }
        Result r = run(s, acts, dependencies.findByProjectId(projectId));
        Instant now = now();
        LocalDate previous = s.getBaselineFinish();
        for (Activity a : acts) {
            Timing t = r.timings().get(a.getId());
            a.baseline(t.start(), t.finish(), now);
        }
        s.baselined(me.getId(), r.plannedFinish(), note, now);
        audit.record(tenantId, s.getBaselineCount() == 1 ? "schedule.baselined" : "schedule.rebaselined", "Project",
                projectId, JsonText.object("finish", r.plannedFinish(), "previousFinish", previous, "note", note,
                        "baseline", s.getBaselineCount()));
        return view(s, me, List.of());
    }

    @Transactional
    public ScheduleView progress(UUID tenantId, UUID projectId, UUID activityId, ProgressRequest req) {
        User me = currentUser.require();
        Schedule s = schedules.lock(tenantId, projectId)
                .orElseThrow(() -> new NotFoundException("This project has no schedule"));
        List<Activity> acts = activities.findByTenantIdAndProjectId(tenantId, projectId);
        Activity a = acts.stream().filter(x -> x.getId().equals(activityId)).findFirst()
                .orElseThrow(() -> new NotFoundException("Activity not found"));
        if (!isEditor(me) && !me.getId().equals(a.getOwnerId())) {
            throw new ForbiddenActionException("Project managers, the PMO and the activity owner record progress");
        }
        boolean clearing = req.actualStart() == null && req.actualFinish() == null
                && (req.percent() == null || req.percent().signum() == 0);
        if (clearing && a.hasProgress() && !isEditor(me)) {
            throw new ForbiddenActionException("Only project managers and the PMO can clear recorded progress");
        }
        if (acts.stream().anyMatch(x -> activityId.equals(x.getParentId()))) {
            throw new BusinessRuleException("Record progress on the activities under \"" + a.getName() + "\"");
        }
        Instant now = now();
        a.progress(req.actualStart(), req.actualFinish(), req.percent(), req.note(), today(), me.getId(), now);
        Result r = run(s, acts, dependencies.findByProjectId(projectId));
        snapshot(s, r, me, now);
        audit.record(tenantId, "schedule.progress", "Project", projectId,
                JsonText.object("activity", a.getName(), "percent", a.getPercentComplete(), "actualStart",
                        a.getActualStart(), "actualFinish", a.getActualFinish(), "note", req.note(),
                        "earnedPct", r.earnedPct(), "plannedPct", r.plannedPct()));
        return view(s, me, List.of());
    }

    @Transactional
    public ScheduleView importMsProject(UUID tenantId, UUID projectId, MultipartFile file) {
        User me = requireEditor("import schedules");
        projects.get(tenantId, projectId);
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("Choose the MS Project XML file");
        }
        if (file.getSize() > MAX_IMPORT_BYTES) {
            throw new BusinessRuleException("The file is larger than 10 MB; save the plan as MS Project XML without "
                    + "resources or attachments");
        }
        Parsed parsed;
        try (InputStream in = file.getInputStream()) {
            parsed = MsProjectImport.parse(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Instant now = now();
        Schedule s = schedules.lock(tenantId, projectId).orElse(null);
        List<Activity> old = activities.findByTenantIdAndProjectId(tenantId, projectId);
        if (old.stream().anyMatch(Activity::hasProgress)) {
            throw new BusinessRuleException("This schedule already has progress recorded; importing would replace it");
        }
        List<String> notices = new ArrayList<>(parsed.warnings());
        if (s == null) {
            s = schedules.save(new Schedule(tenantId, projectId, parsed.projectStart(), now));
        } else {
            if (s.getBaselineCount() > 0) {
                // Replacing a baselined plan discards the baseline, so only the PMO may do it.
                Roles.requireAny(me, Set.of(Roles.PMO_LEAD), "import over a baselined schedule");
                s.clearBaseline(now);
                notices.add("The previous baseline was discarded; set a new baseline once the plan is checked");
            }
            s.setStartDate(parsed.projectStart(), now);
        }
        dependencies.deleteAll(dependencies.findByProjectId(projectId));
        dependencies.flush();
        deleteDeepestFirst(old);
        activities.flush();

        Map<Integer, Activity> byUid = new HashMap<>();
        Deque<Map.Entry<Integer, Activity>> stack = new ArrayDeque<>(); // (outline level, activity)
        Map<UUID, Integer> nextOrder = new HashMap<>();
        UUID rootKey = UUID.randomUUID();
        LocalDate today = today();
        List<Activity> created = new ArrayList<>();
        for (ImportedTask t : parsed.tasks()) {
            while (!stack.isEmpty() && stack.peek().getKey() >= t.outlineLevel()) {
                stack.pop();
            }
            Activity parent = stack.isEmpty() ? null : stack.peek().getValue();
            Activity a = new Activity(tenantId, projectId, now);
            int order = nextOrder.merge(parent == null ? rootKey : parent.getId(), 1, Integer::sum);
            a.define(parent == null ? null : parent.getId(), order, t.name(), null, t.summary() ? 0 : t.durationDays(),
                    t.summary() ? null : t.start(), null, null, now);
            activities.save(a);
            created.add(a);
            byUid.put(t.uid(), a);
            stack.push(Map.entry(t.outlineLevel(), a));
            if (!t.summary() && (t.actualStart() != null || t.percent().signum() > 0)) {
                try {
                    a.progress(t.actualStart(), t.actualFinish(), t.percent(), "Imported from MS Project", today,
                            me.getId(), now);
                } catch (BusinessRuleException e) {
                    notices.add("\"" + t.name() + "\": progress not imported (" + e.getMessage() + ")");
                }
            }
        }
        Set<UUID> parents = created.stream().map(Activity::getParentId).filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        for (Activity a : created) {
            // A task can end up as a parent when MS Project's own summary above it was skipped.
            if (parents.contains(a.getId()) && a.hasProgress()) {
                a.progress(null, null, null, null, today, me.getId(), now);
                notices.add("\"" + a.getName() + "\" now has activities under it, so its progress was not imported");
            }
        }
        activities.flush();
        List<Dependency> links = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ImportedLink l : parsed.links()) {
            Activity pred = byUid.get(l.predecessorUid());
            Activity succ = byUid.get(l.successorUid());
            if (pred == null || succ == null || pred == succ) {
                continue;
            }
            if (parents.contains(pred.getId()) || parents.contains(succ.getId())) {
                notices.add("A link to or from summary task \"" + (parents.contains(pred.getId()) ? pred : succ).getName()
                        + "\" was not imported; link the activities under it instead");
                continue;
            }
            if (seen.add(pred.getId() + ">" + succ.getId())) {
                links.add(new Dependency(projectId, pred.getId(), succ.getId(), l.type(), l.lagDays()));
            }
        }
        run(s, created, links);
        dependencies.saveAll(links);
        s.changed(now);
        audit.record(tenantId, "schedule.imported", "Project", projectId,
                JsonText.object("file", file.getOriginalFilename(), "activities", created.size(),
                        "dependencies", links.size(), "warnings", notices.size()));
        return view(s, me, notices);
    }

    // ================================================================== helpers

    private void snapshot(Schedule s, Result r, User me, Instant now) {
        LocalDate today = today();
        ProgressSnapshot snap = snapshots.findByProjectIdAndAsOf(s.getProjectId(), today)
                .orElseGet(() -> new ProgressSnapshot(s.getTenantId(), s.getProjectId(), today, now));
        snap.record(r.plannedPct(), r.earnedPct(), me.getId(), now);
        snapshots.save(snap);
    }

    private void checkOwners(UUID tenantId, List<UUID> ownerIds) {
        Set<UUID> ids = new HashSet<>(ownerIds);
        ids.remove(null);
        if (ids.isEmpty()) {
            return;
        }
        Set<UUID> active = currentUser.byIds(tenantId, ids).stream().filter(User::isActive).map(User::getId)
                .collect(Collectors.toSet());
        if (!active.containsAll(ids)) {
            throw new BusinessRuleException("Activity owners must be active users of this organisation");
        }
    }

    private void deleteDeepestFirst(List<Activity> list) {
        Map<UUID, Activity> byId = list.stream().collect(Collectors.toMap(Activity::getId, Function.identity()));
        Map<UUID, Integer> depth = new HashMap<>();
        for (Activity a : list) {
            int d = 0;
            UUID p = a.getParentId();
            while (p != null && byId.containsKey(p) && d < 60) {
                d++;
                p = byId.get(p).getParentId();
            }
            depth.put(a.getId(), d);
        }
        list.stream().sorted(Comparator.comparingInt((Activity a) -> depth.get(a.getId())).reversed())
                .forEach(activities::delete);
    }

    private static Activity existingFor(String key, Map<UUID, Activity> existing) {
        try {
            return existing.get(UUID.fromString(key));
        } catch (IllegalArgumentException notAUuid) {
            return null;
        }
    }

    private Result run(Schedule s, List<Activity> acts, List<Dependency> links) {
        return ScheduleEngine.run(s.getStartDate(), today(), acts.stream().map(Activity::toTask).toList(),
                links.stream().map(Dependency::toLink).toList());
    }

    private User requireEditor(String action) {
        User me = currentUser.require();
        Roles.requireAny(me, Roles.PLAN_EDITORS, action);
        return me;
    }

    private static boolean isEditor(User me) {
        return me.getRoles().stream().anyMatch(Roles.PLAN_EDITORS::contains);
    }

    private Instant now() {
        return Instant.now(clock);
    }

    private LocalDate today() {
        return LocalDate.ofInstant(now(), LOCAL_ZONE);
    }

    private ScheduleView view(Schedule s, User me, List<String> notices) {
        UUID tenantId = s.getTenantId();
        List<Activity> acts = activities.findByTenantIdAndProjectId(tenantId, s.getProjectId());
        List<Dependency> links = dependencies.findByProjectId(s.getProjectId());
        boolean editor = isEditor(me);
        boolean pmo = me.hasRole(Roles.PMO_LEAD);
        Permissions can = new Permissions(editor, !acts.isEmpty() && (s.getBaselineCount() == 0 ? editor : pmo),
                (s.getBaselineCount() == 0 ? editor : pmo) && acts.stream().noneMatch(Activity::hasProgress));
        Set<UUID> userIds = new HashSet<>();
        acts.forEach(a -> {
            if (a.getOwnerId() != null) {
                userIds.add(a.getOwnerId());
            }
            if (a.getProgressUpdatedBy() != null) {
                userIds.add(a.getProgressUpdatedBy());
            }
        });
        if (s.getBaselineSetBy() != null) {
            userIds.add(s.getBaselineSetBy());
        }
        Map<UUID, User> people = userIds.isEmpty() ? Map.of() : currentUser.byIds(tenantId, userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        if (acts.isEmpty()) {
            return new ScheduleView(true, s.getProjectId(), s.getStartDate(), s.getBaselineSetAt(),
                    ref(people, s.getBaselineSetBy()), s.getBaselineNote(), s.getBaselineCount(), s.getVersion(),
                    List.of(), null, List.of(), List.of(), notices, can);
        }
        Result r = run(s, acts, links);
        Map<UUID, Activity> byId = acts.stream().collect(Collectors.toMap(Activity::getId, Function.identity()));
        Map<UUID, List<Dependency>> predsOf = links.stream().collect(Collectors.groupingBy(Dependency::getSuccessorId));

        List<ActivityView> rows = r.order().stream().map(id -> {
            Activity a = byId.get(id);
            Timing t = r.timings().get(id);
            List<PredecessorView> preds = predsOf.getOrDefault(id, List.of()).stream()
                    .map(d -> new PredecessorView(d.getPredecessorId(), r.timings().get(d.getPredecessorId()).wbs(),
                            byId.get(d.getPredecessorId()).getName(), d.getType(), d.getLagDays()))
                    .sorted(Comparator.comparing(PredecessorView::wbs)).toList();
            return new ActivityView(id, a.getParentId(), t.wbs(), t.depth(), t.summary(), a.getName(), a.getDescription(),
                    a.getDurationDays(), a.getEarliestStart(), a.getWeight(), ref(people, a.getOwnerId()), t.start(),
                    t.finish(), t.totalFloat(), t.critical(), a.getBaselineStart(), a.getBaselineFinish(),
                    t.forecastStart(), t.forecastFinish(), a.getActualStart(), a.getActualFinish(), t.percent(),
                    a.getProgressNote(), a.getProgressUpdatedAt(), ref(people, a.getProgressUpdatedBy()), t.late(),
                    !t.summary() && (editor || me.getId().equals(a.getOwnerId())), preds);
        }).toList();

        List<Task> leaves = acts.stream().filter(a -> !r.timings().get(a.getId()).summary()).map(Activity::toTask).toList();
        boolean baselined = r.baselineFinish() != null;
        LocalDate end = baselined && r.baselineFinish().isAfter(r.plannedFinish()) ? r.baselineFinish() : r.plannedFinish();
        if (r.forecastFinish().isAfter(end)) {
            end = r.forecastFinish();
        }
        List<CurvePoint> planned = new ArrayList<>();
        for (LocalDate d = s.getStartDate(); !d.isAfter(end) && planned.size() < 400; d = d.plusDays(7)) {
            planned.add(new CurvePoint(d, ScheduleEngine.plannedPctAt(leaves, r.timings(), baselined, d), null));
        }
        planned.add(new CurvePoint(end, ScheduleEngine.plannedPctAt(leaves, r.timings(), baselined, end), null));
        List<CurvePoint> actual = snapshots.findByTenantIdAndProjectIdOrderByAsOfAsc(tenantId, s.getProjectId()).stream()
                .map(p -> new CurvePoint(p.getAsOf(), p.getPlannedPct(), p.getEarnedPct())).toList();

        Metrics m = new Metrics(s.getStartDate(), r.plannedFinish(), r.baselineFinish(), r.forecastFinish(), r.slipDays(),
                r.plannedPct(), r.earnedPct(), r.spi(), leaves.size(),
                (int) leaves.stream().filter(t -> r.timings().get(t.id()).critical()).count(),
                (int) leaves.stream().filter(t -> r.timings().get(t.id()).late()).count());
        return new ScheduleView(true, s.getProjectId(), s.getStartDate(), s.getBaselineSetAt(),
                ref(people, s.getBaselineSetBy()), s.getBaselineNote(), s.getBaselineCount(), s.getVersion(), rows, m,
                planned, actual, notices, can);
    }

    private static Ref ref(Map<UUID, User> people, UUID id) {
        if (id == null) {
            return null;
        }
        User u = people.get(id);
        return new Ref(id, u == null ? "Unknown user" : u.getDisplayName());
    }
}
