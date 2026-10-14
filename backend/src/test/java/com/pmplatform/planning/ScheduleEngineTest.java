package com.pmplatform.planning;

import static org.assertj.core.api.Assertions.assertThat;

import com.pmplatform.planning.ScheduleEngine.Link;
import com.pmplatform.planning.ScheduleEngine.Result;
import com.pmplatform.planning.ScheduleEngine.Task;
import com.pmplatform.platform.web.BusinessRuleException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ScheduleEngineTest {

    static final LocalDate START = LocalDate.of(2026, 11, 2);
    final UUID phase = UUID.randomUUID();
    final UUID survey = UUID.randomUUID();
    final UUID design = UUID.randomUUID();
    final UUID wayleave = UUID.randomUUID();
    final UUID build = UUID.randomUUID();
    final UUID done = UUID.randomUUID();

    static Task task(UUID id, UUID parent, int order, String name, int days) {
        return new Task(id, parent, order, name, days, null, null, null, null, null, null, BigDecimal.ZERO);
    }

    static Task progress(Task t, LocalDate as, LocalDate af, int pct) {
        return new Task(t.id(), t.parentId(), t.sortOrder(), t.name(), t.durationDays(), t.earliestStart(), t.weight(),
                t.baselineStart(), t.baselineFinish(), as, af, BigDecimal.valueOf(pct));
    }

    static Task baseline(Task t, Result r) {
        return new Task(t.id(), t.parentId(), t.sortOrder(), t.name(), t.durationDays(), t.earliestStart(), t.weight(),
                r.timings().get(t.id()).start(), r.timings().get(t.id()).finish(), t.actualStart(), t.actualFinish(),
                t.percent());
    }

    /** Survey 5d -> design 10d -> build 20d -> commissioned (milestone); wayleave 8d (SS+2 after survey) -> build. */
    List<Task> tasks() {
        return new ArrayList<>(List.of(
                task(phase, null, 1, "Line construction", 0),
                task(survey, phase, 1, "Survey", 5),
                task(design, phase, 2, "Design", 10),
                task(wayleave, phase, 3, "Wayleave consent", 8),
                task(build, phase, 4, "Build", 20),
                task(done, null, 2, "Commissioned", 0)));
    }

    List<Link> links() {
        return List.of(new Link(survey, design, DependencyType.FS, 0), new Link(design, build, DependencyType.FS, 0),
                new Link(survey, wayleave, DependencyType.SS, 2), new Link(wayleave, build, DependencyType.FS, 0),
                new Link(build, done, DependencyType.FS, 0));
    }

    @Test
    void criticalPathAndFloat() {
        Result r = ScheduleEngine.run(START, START, tasks(), links());
        assertThat(r.timings().get(survey).start()).isEqualTo(START);
        assertThat(r.timings().get(survey).finish()).isEqualTo(START.plusDays(4));
        assertThat(r.timings().get(design).start()).isEqualTo(START.plusDays(5));
        assertThat(r.timings().get(wayleave).start()).isEqualTo(START.plusDays(2));   // SS + 2
        assertThat(r.timings().get(build).start()).isEqualTo(START.plusDays(15));
        assertThat(r.timings().get(done).start()).isEqualTo(START.plusDays(35));      // milestone on the day after build
        assertThat(r.plannedFinish()).isEqualTo(START.plusDays(35));
        assertThat(r.timings().get(design).critical()).isTrue();
        assertThat(r.timings().get(wayleave).totalFloat()).isEqualTo(5);                // finishes day 9, build starts 15
        assertThat(r.timings().get(wayleave).critical()).isFalse();
        assertThat(r.timings().get(phase).summary()).isTrue();
        assertThat(r.timings().get(phase).start()).isEqualTo(START);
        assertThat(r.timings().get(phase).finish()).isEqualTo(START.plusDays(34));
        assertThat(r.timings().get(build).wbs()).isEqualTo("1.4");
        assertThat(r.timings().get(done).wbs()).isEqualTo("2");
    }

    @Test
    void lagAndEarliestStartPushDates() {
        List<Task> t = tasks();
        t.set(3, new Task(wayleave, phase, 3, "Wayleave consent", 8, START.plusDays(20), null, null, null, null, null,
                BigDecimal.ZERO));
        Result r = ScheduleEngine.run(START, START, t, links());
        assertThat(r.timings().get(build).start()).isEqualTo(START.plusDays(28));       // wayleave now drives
        assertThat(r.timings().get(wayleave).critical()).isTrue();
        assertThat(r.timings().get(design).totalFloat()).isEqualTo(13);
    }

    @Test
    void loopsAreRefused() {
        List<Link> loop = new ArrayList<>(links());
        loop.add(new Link(build, survey, DependencyType.FS, 0));
        try {
            ScheduleEngine.run(START, START, tasks(), loop);
            throw new AssertionError("expected a loop error");
        } catch (BusinessRuleException expected) {
            assertThat(expected.getMessage()).contains("loop");
        }
        try {
            ScheduleEngine.run(START, START, tasks(), List.of(new Link(phase, done, DependencyType.FS, 0)));
            throw new AssertionError("expected a summary error");
        } catch (BusinessRuleException expected) {
            assertThat(expected.getMessage()).contains("summary");
        }
    }

    @Test
    void forecastFollowsProgressAndSlips() {
        Result plan = ScheduleEngine.run(START, START, tasks(), links());
        List<Task> t = new ArrayList<>(tasks().stream().map(x -> baseline(x, plan)).toList());
        LocalDate today = START.plusDays(10);
        // Survey finished 2 days late (day 6); design started day 7 and is 30% done.
        t.set(1, progress(t.get(1), START, START.plusDays(6), 100));
        t.set(2, progress(t.get(2), START.plusDays(7), null, 30));
        Result r = ScheduleEngine.run(START, today, t, links());
        // Design: 10 days * 70% = 7 remaining from day 10 -> ends day 16 inclusive.
        assertThat(r.timings().get(design).forecastFinish()).isEqualTo(START.plusDays(16));
        // Wayleave has not started (it should have, on day 2): starting today it ends day 17, so it now
        // drives the build (day 18) rather than design (ends day 16).
        assertThat(r.timings().get(build).forecastStart()).isEqualTo(START.plusDays(18));
        assertThat(r.forecastFinish()).isEqualTo(START.plusDays(38));
        assertThat(r.baselineFinish()).isEqualTo(START.plusDays(35));
        assertThat(r.slipDays()).isEqualTo(3);
        // Wayleave not started, planned days 2-9: late.
        assertThat(r.timings().get(wayleave).late()).isTrue();
        assertThat(r.timings().get(wayleave).forecastStart()).isEqualTo(today);
    }

    @Test
    void rowsAddedAfterTheBaselineKeepTheBaselineAndMilestonesFinishOnTheirDate() {
        Result plan = ScheduleEngine.run(START, START, tasks(), links());
        List<Task> t = new ArrayList<>(tasks().stream().map(x -> baseline(x, plan)).toList());
        UUID extra = UUID.randomUUID();
        t.add(task(extra, phase, 5, "Added later", 3)); // no baseline of its own
        Result r = ScheduleEngine.run(START, START, t, links());
        assertThat(r.baselineFinish()).isEqualTo(START.plusDays(35));
        assertThat(r.timings().get(build).late()).isFalse();

        // A milestone signed off two days after it was first recorded finishes on its sign-off date.
        t.set(5, progress(t.get(5), START.plusDays(1), START.plusDays(3), 100));
        Result m = ScheduleEngine.run(START, START.plusDays(5), t, links());
        assertThat(m.timings().get(done).forecastFinish()).isEqualTo(START.plusDays(3));
    }

    @Test
    void plannedAndEarnedProgress() {
        Result plan = ScheduleEngine.run(START, START, tasks(), links());
        List<Task> t = new ArrayList<>(tasks().stream().map(x -> baseline(x, plan)).toList());
        // Weights = durations: survey 5, design 10, wayleave 8, build 20, milestone 0 -> 43.
        t.set(1, progress(t.get(1), START, START.plusDays(4), 100));
        t.set(2, progress(t.get(2), START.plusDays(5), null, 50));
        Result r = ScheduleEngine.run(START, START.plusDays(9), t, links());
        // Earned: (5*100 + 10*50) / 43 = 23.26
        assertThat(r.earnedPct()).isEqualByComparingTo("23.26");
        // Planned on day 9: survey 5/5, design 5/10, wayleave 8/8 -> (5 + 5 + 8) / 43 = 41.86
        assertThat(r.plannedPct()).isEqualByComparingTo("41.86");
        assertThat(r.spi()).isEqualByComparingTo("0.556");
        assertThat(r.timings().get(phase).percent()).isEqualByComparingTo("23.26");
    }
}
