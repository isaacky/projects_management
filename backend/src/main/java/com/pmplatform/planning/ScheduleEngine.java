package com.pmplatform.planning;

import com.pmplatform.platform.web.BusinessRuleException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Critical path scheduling in calendar days, plus progress measures. Pure functions.
 *
 * <p>Days are counted from the schedule start (day 0). An activity of duration d starting on day s
 * occupies days s .. s+d-1; a milestone (d = 0) sits on day s. A finish-to-start successor may start
 * the day after its predecessor finishes (plus lag); a start-to-start successor on the predecessor's
 * start day (plus lag). Activities with children are summaries: their dates and progress roll up.
 *
 * <ul>
 *   <li><b>Plan</b>: early and late dates, total float, critical path (float 0).</li>
 *   <li><b>Forecast</b> as of a data date: finished work at its actual dates, work in progress
 *       finishing after its remaining duration, work not started no earlier than the data date.</li>
 *   <li><b>Progress</b>: planned % (against the baseline when there is one) and earned % (weighted
 *       % complete); SPI = earned / planned.</li>
 * </ul>
 */
public final class ScheduleEngine {

    private ScheduleEngine() {
    }

    public record Task(UUID id, UUID parentId, int sortOrder, String name, int durationDays, LocalDate earliestStart,
            BigDecimal weight, LocalDate baselineStart, LocalDate baselineFinish, LocalDate actualStart,
            LocalDate actualFinish, BigDecimal percent) {
    }

    public record Link(UUID predecessor, UUID successor, DependencyType type, int lagDays) {
    }

    public record Timing(String wbs, int depth, boolean summary, LocalDate start, LocalDate finish, int totalFloat,
            boolean critical, LocalDate forecastStart, LocalDate forecastFinish, BigDecimal percent,
            BigDecimal weight, boolean late) {
    }

    public record Result(List<UUID> order, Map<UUID, Timing> timings, LocalDate plannedFinish,
            LocalDate forecastFinish, LocalDate baselineFinish, BigDecimal plannedPct, BigDecimal earnedPct,
            BigDecimal spi, int slipDays) {
    }

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public static Result run(LocalDate projectStart, LocalDate dataDate, List<Task> tasks, List<Link> links) {
        Map<UUID, Task> byId = new LinkedHashMap<>();
        tasks.forEach(t -> byId.put(t.id(), t));
        Map<UUID, List<Task>> children = new HashMap<>();
        for (Task t : tasks) {
            if (t.parentId() != null) {
                if (!byId.containsKey(t.parentId())) {
                    throw new BusinessRuleException("Activity \"" + t.name() + "\" has an unknown parent");
                }
                children.computeIfAbsent(t.parentId(), k -> new ArrayList<>()).add(t);
            }
        }
        children.values().forEach(l -> l.sort(Comparator.comparingInt(Task::sortOrder)));

        // Display order (depth first) and WBS codes; also rejects parent loops.
        List<UUID> order = new ArrayList<>();
        Map<UUID, String> wbs = new HashMap<>();
        Map<UUID, Integer> depth = new HashMap<>();
        List<Task> roots = tasks.stream().filter(t -> t.parentId() == null)
                .sorted(Comparator.comparingInt(Task::sortOrder)).toList();
        for (int i = 0; i < roots.size(); i++) {
            walk(roots.get(i), String.valueOf(i + 1), 0, children, order, wbs, depth);
        }
        if (order.size() != tasks.size()) {
            throw new BusinessRuleException("The activity outline has a loop (an activity is its own parent)");
        }

        List<Task> leaves = tasks.stream().filter(t -> !children.containsKey(t.id())).toList();
        Map<UUID, List<Link>> preds = new HashMap<>();
        Map<UUID, List<Link>> succs = new HashMap<>();
        for (Link l : links) {
            if (children.containsKey(l.predecessor()) || children.containsKey(l.successor())) {
                throw new BusinessRuleException("Dependencies link activities, not summary rows");
            }
            if (!byId.containsKey(l.predecessor()) || !byId.containsKey(l.successor())) {
                throw new BusinessRuleException("A dependency refers to an unknown activity");
            }
            preds.computeIfAbsent(l.successor(), k -> new ArrayList<>()).add(l);
            succs.computeIfAbsent(l.predecessor(), k -> new ArrayList<>()).add(l);
        }
        List<Task> topo = topological(leaves, preds, succs, byId);

        // ---- plan: forward and backward pass
        Map<UUID, Integer> es = new HashMap<>();
        Map<UUID, Integer> ef = new HashMap<>();
        for (Task t : topo) {
            int start = Math.max(0, offset(projectStart, t.earliestStart()));
            for (Link l : preds.getOrDefault(t.id(), List.of())) {
                start = Math.max(start, (l.type() == DependencyType.FS ? ef.get(l.predecessor()) : es.get(l.predecessor()))
                        + l.lagDays());
            }
            es.put(t.id(), start);
            ef.put(t.id(), start + t.durationDays());
        }
        int projectEnd = leaves.stream().mapToInt(t -> ef.get(t.id())).max().orElse(0);
        Map<UUID, Integer> lf = new HashMap<>();
        Map<UUID, Integer> ls = new HashMap<>();
        for (int i = topo.size() - 1; i >= 0; i--) {
            Task t = topo.get(i);
            int finish = projectEnd;
            for (Link l : succs.getOrDefault(t.id(), List.of())) {
                int lsSucc = ls.get(l.successor());
                finish = Math.min(finish, l.type() == DependencyType.FS ? lsSucc - l.lagDays()
                        : lsSucc - l.lagDays() + t.durationDays());
            }
            lf.put(t.id(), finish);
            ls.put(t.id(), finish - t.durationDays());
        }

        // ---- forecast from the data date
        int d = offset(projectStart, dataDate);
        Map<UUID, Integer> fs = new HashMap<>();
        Map<UUID, Integer> fe = new HashMap<>();
        for (Task t : topo) {
            int s;
            int e;
            if (t.actualFinish() != null) {
                s = offset(projectStart, t.actualStart());
                e = t.durationDays() == 0 ? offset(projectStart, t.actualFinish()) : offset(projectStart, t.actualFinish()) + 1;
            } else if (t.actualStart() != null) {
                s = offset(projectStart, t.actualStart());
                int remaining = (int) Math.ceil(t.durationDays() * (100 - pct(t).doubleValue()) / 100.0);
                e = Math.max(d, s) + remaining;
            } else {
                s = Math.max(Math.max(0, offset(projectStart, t.earliestStart())), d);
                for (Link l : preds.getOrDefault(t.id(), List.of())) {
                    s = Math.max(s, (l.type() == DependencyType.FS ? fe.get(l.predecessor()) : fs.get(l.predecessor()))
                            + l.lagDays());
                }
                e = s + t.durationDays();
            }
            fs.put(t.id(), s);
            fe.put(t.id(), e);
        }

        // ---- leaves
        Map<UUID, Timing> timings = new HashMap<>();
        // Rows added after the baseline have none of their own; they are measured against the current plan.
        boolean baselined = leaves.stream().anyMatch(ScheduleEngine::hasBaseline);
        for (Task t : leaves) {
            int fl = ls.get(t.id()) - es.get(t.id());
            LocalDate plannedFinish = finishDate(projectStart, es.get(t.id()), ef.get(t.id()), t.durationDays());
            LocalDate dueFinish = hasBaseline(t) ? t.baselineFinish() : plannedFinish;
            boolean late = t.actualFinish() == null && dueFinish.isBefore(dataDate);
            timings.put(t.id(), new Timing(wbs.get(t.id()), depth.get(t.id()), false,
                    projectStart.plusDays(es.get(t.id())), plannedFinish, fl, fl <= 0,
                    projectStart.plusDays(fs.get(t.id())),
                    finishDate(projectStart, fs.get(t.id()), fe.get(t.id()), t.durationDays()),
                    pct(t), weight(t), late));
        }
        // ---- summaries, deepest first
        List<UUID> summaries = new ArrayList<>(order.stream().filter(children::containsKey).toList());
        summaries.sort(Comparator.comparingInt((UUID id) -> depth.get(id)).reversed());
        for (UUID id : summaries) {
            List<Timing> kids = children.get(id).stream().map(k -> timings.get(k.id())).toList();
            BigDecimal w = kids.stream().map(Timing::weight).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal p = w.signum() == 0 ? BigDecimal.ZERO : kids.stream()
                    .map(k -> k.weight().multiply(k.percent())).reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(w, 2, RoundingMode.HALF_UP);
            timings.put(id, new Timing(wbs.get(id), depth.get(id), true,
                    kids.stream().map(Timing::start).min(Comparator.naturalOrder()).orElse(projectStart),
                    kids.stream().map(Timing::finish).max(Comparator.naturalOrder()).orElse(projectStart),
                    kids.stream().mapToInt(Timing::totalFloat).min().orElse(0),
                    kids.stream().anyMatch(Timing::critical),
                    kids.stream().map(Timing::forecastStart).min(Comparator.naturalOrder()).orElse(projectStart),
                    kids.stream().map(Timing::forecastFinish).max(Comparator.naturalOrder()).orElse(projectStart),
                    p, w, kids.stream().anyMatch(Timing::late)));
        }

        LocalDate plannedFinish = leaves.stream().map(t -> timings.get(t.id()).finish())
                .max(Comparator.naturalOrder()).orElse(projectStart);
        LocalDate forecastFinish = leaves.stream().map(t -> timings.get(t.id()).forecastFinish())
                .max(Comparator.naturalOrder()).orElse(projectStart);
        LocalDate baselineFinish = baselined ? leaves.stream().filter(ScheduleEngine::hasBaseline)
                .map(Task::baselineFinish).max(Comparator.naturalOrder()).orElse(null) : null;
        BigDecimal planned = plannedPctAt(leaves, timings, baselined, dataDate);
        BigDecimal earned = earnedPct(leaves);
        BigDecimal spi = planned.signum() == 0 ? null
                : earned.divide(planned, 3, RoundingMode.HALF_UP).min(new BigDecimal("99.999"));
        int slip = (int) ChronoUnit.DAYS.between(baselineFinish != null ? baselineFinish : plannedFinish, forecastFinish);
        return new Result(order, timings, plannedFinish, forecastFinish, baselineFinish, planned, earned, spi, slip);
    }

    /**
     * Planned % complete at a date: each row against its baseline when the schedule is baselined and the row has
     * one, otherwise against the current plan.
     */
    public static BigDecimal plannedPctAt(List<Task> leaves, Map<UUID, Timing> timings, boolean baselined, LocalDate at) {
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal done = BigDecimal.ZERO;
        for (Task t : leaves) {
            BigDecimal w = weight(t);
            boolean b = baselined && hasBaseline(t);
            LocalDate s = b ? t.baselineStart() : timings.get(t.id()).start();
            LocalDate f = b ? t.baselineFinish() : timings.get(t.id()).finish();
            long days = ChronoUnit.DAYS.between(s, f) + 1;
            double frac;
            if (t.durationDays() == 0 || days <= 0) {
                frac = at.isBefore(s) ? 0 : 1;
            } else {
                frac = Math.max(0, Math.min(1, (ChronoUnit.DAYS.between(s, at) + 1) / (double) days));
            }
            total = total.add(w);
            done = done.add(w.multiply(BigDecimal.valueOf(frac)));
        }
        return total.signum() == 0 ? BigDecimal.ZERO : done.multiply(HUNDRED).divide(total, 2, RoundingMode.HALF_UP);
    }

    public static BigDecimal earnedPct(List<Task> leaves) {
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal earned = BigDecimal.ZERO;
        for (Task t : leaves) {
            total = total.add(weight(t));
            earned = earned.add(weight(t).multiply(pct(t)));
        }
        return total.signum() == 0 ? BigDecimal.ZERO : earned.divide(total, 2, RoundingMode.HALF_UP);
    }

    /** Weight towards progress: the given weight, or the duration. */
    public static BigDecimal weight(Task t) {
        return t.weight() != null ? t.weight() : BigDecimal.valueOf(t.durationDays());
    }

    private static BigDecimal pct(Task t) {
        return t.percent() == null ? BigDecimal.ZERO : t.percent();
    }

    private static boolean hasBaseline(Task t) {
        return t.baselineStart() != null && t.baselineFinish() != null;
    }

    private static LocalDate finishDate(LocalDate base, int start, int endExclusive, int duration) {
        // A milestone finishes on its start, or on its recorded finish date when that is later.
        return duration == 0 ? base.plusDays(Math.max(start, endExclusive)) : base.plusDays(endExclusive - 1L);
    }

    private static int offset(LocalDate base, LocalDate date) {
        return date == null ? 0 : (int) ChronoUnit.DAYS.between(base, date);
    }

    private static void walk(Task t, String code, int level, Map<UUID, List<Task>> children, List<UUID> order,
            Map<UUID, String> wbs, Map<UUID, Integer> depth) {
        if (wbs.containsKey(t.id()) || level > 50) {
            return;
        }
        order.add(t.id());
        wbs.put(t.id(), code);
        depth.put(t.id(), level);
        List<Task> kids = children.getOrDefault(t.id(), List.of());
        for (int i = 0; i < kids.size(); i++) {
            walk(kids.get(i), code + "." + (i + 1), level + 1, children, order, wbs, depth);
        }
    }

    private static List<Task> topological(List<Task> leaves, Map<UUID, List<Link>> preds, Map<UUID, List<Link>> succs,
            Map<UUID, Task> byId) {
        Map<UUID, Integer> indegree = new HashMap<>();
        leaves.forEach(t -> indegree.put(t.id(), preds.getOrDefault(t.id(), List.of()).size()));
        Deque<Task> ready = new ArrayDeque<>();
        leaves.stream().filter(t -> indegree.get(t.id()) == 0).forEach(ready::add);
        List<Task> out = new ArrayList<>();
        while (!ready.isEmpty()) {
            Task t = ready.poll();
            out.add(t);
            for (Link l : succs.getOrDefault(t.id(), List.of())) {
                if (indegree.merge(l.successor(), -1, Integer::sum) == 0) {
                    ready.add(byId.get(l.successor()));
                }
            }
        }
        if (out.size() != leaves.size()) {
            String names = leaves.stream().filter(t -> indegree.get(t.id()) > 0).map(Task::name).limit(5)
                    .reduce((a, b) -> a + ", " + b).orElse("");
            throw new BusinessRuleException("The dependencies form a loop involving: " + names);
        }
        return out;
    }
}
