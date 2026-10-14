package com.pmplatform.procurement.plan;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class PlanRulesTest {

    static final LocalDate FY = LocalDate.of(2026, 7, 1);

    @Test
    void financialYearLabelsAndQuarters() {
        assertThat(PlanRules.label(FY)).isEqualTo("2026/27");
        assertThat(PlanRules.label(LocalDate.of(2099, 7, 1))).isEqualTo("2099/00");
        assertThat(PlanRules.label(LocalDate.of(2027, 1, 1))).isEqualTo("2027");
        assertThat(PlanRules.fyEnd(FY)).isEqualTo(LocalDate.of(2027, 6, 30));
        assertThat(PlanRules.quarterOf(FY, LocalDate.of(2026, 7, 1))).isEqualTo(1);
        assertThat(PlanRules.quarterOf(FY, LocalDate.of(2026, 9, 30))).isEqualTo(1);
        assertThat(PlanRules.quarterOf(FY, LocalDate.of(2026, 10, 1))).isEqualTo(2);
        assertThat(PlanRules.quarterOf(FY, LocalDate.of(2027, 6, 30))).isEqualTo(4);
        assertThat(PlanRules.quarterOf(FY, LocalDate.of(2027, 7, 1))).isEqualTo(0);
        assertThat(PlanRules.quarterEnd(FY, 2)).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(PlanRules.quarterStart(FY, 3)).isEqualTo(LocalDate.of(2027, 1, 1));
    }

    @Test
    void stageBehindAndShares() {
        assertThat(PlanRules.stage(0, 0, 0)).isEqualTo(LineStage.NOT_STARTED);
        assertThat(PlanRules.stage(10, 0, 0)).isEqualTo(LineStage.REQUISITIONED);
        assertThat(PlanRules.stage(10, 1, 0)).isEqualTo(LineStage.TENDERING);
        assertThat(PlanRules.stage(10, 0, 9)).isEqualTo(LineStage.AWARDED);
        LocalDate oct = LocalDate.of(2026, 10, 5);
        assertThat(PlanRules.behind(true, LineStage.NOT_STARTED, FY, 1, oct)).isTrue();
        assertThat(PlanRules.behind(true, LineStage.NOT_STARTED, FY, 2, oct)).isFalse();
        assertThat(PlanRules.behind(true, LineStage.REQUISITIONED, FY, 1, oct)).isFalse();
        assertThat(PlanRules.behind(false, LineStage.NOT_STARTED, FY, 1, oct)).isFalse();
        assertThat(PlanRules.pct(30, 90)).isEqualByComparingTo(new BigDecimal("33.3"));
        assertThat(PlanRules.pct(5, 0)).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(PlanRules.balance(100, 30)).isEqualTo(70L);
        assertThat(PlanRules.balance(100, 130)).isZero();
    }
}
