package com.pmplatform.benefits;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class BenefitRulesTest {

    static BigDecimal d(String s) {
        return new BigDecimal(s);
    }

    @Test
    void progressWorksForRisingAndFallingTargets() {
        assertThat(BenefitRules.progressPct(d("0"), d("12000"), d("3000"))).isEqualByComparingTo(d("25"));
        // Estimated bills: 38% down to 5%; at 24% that is 14 of 33 points.
        assertThat(BenefitRules.progressPct(d("38"), d("5"), d("24"))).isEqualByComparingTo(d("42.42"));
        assertThat(BenefitRules.progressPct(d("38"), d("5"), d("40"))).isEqualByComparingTo(d("-6.06"));
        assertThat(BenefitRules.direction(d("38"), d("5"))).isEqualTo(Direction.DECREASE);
        assertThat(BenefitRules.direction(d("0"), d("1"))).isEqualTo(Direction.INCREASE);
    }

    @Test
    void expectedProgressAndRating() {
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 12, 31); // 364 days
        assertThat(BenefitRules.expectedPct(from, to, from.minusDays(3))).isEqualByComparingTo(d("0"));
        assertThat(BenefitRules.expectedPct(from, to, from.plusDays(91))).isEqualByComparingTo(d("25"));
        assertThat(BenefitRules.expectedPct(from, to, to.plusDays(1))).isEqualByComparingTo(d("100"));

        assertThat(BenefitRules.rating(null, d("50"))).isNull();
        assertThat(BenefitRules.rating(d("100"), d("50"))).isEqualTo(BenefitRating.ACHIEVED);
        assertThat(BenefitRules.rating(d("46"), d("50"))).isEqualTo(BenefitRating.ON_TRACK);
        assertThat(BenefitRules.rating(d("40"), d("50"))).isEqualTo(BenefitRating.AT_RISK);
        assertThat(BenefitRules.rating(d("20"), d("50"))).isEqualTo(BenefitRating.OFF_TRACK);
        assertThat(BenefitRules.rating(d("0"), d("0"))).isEqualTo(BenefitRating.ON_TRACK);
        assertThat(BenefitRules.rating(d("-5"), d("0"))).isEqualTo(BenefitRating.OFF_TRACK);
        assertThat(BenefitRules.progressPct(d("0.5"), d("0.4"), d("120"))).isEqualByComparingTo(d("-119500"));
        // A typo far outside the range is capped so it can still be stored and reviewed.
        assertThat(BenefitRules.progressPct(d("0.0001"), d("0.0002"), d("9999"))).isEqualByComparingTo(d("1000000"));
    }

    @Test
    void dueDatesAndRealisedValue() {
        LocalDate base = LocalDate.of(2026, 1, 1);
        assertThat(BenefitRules.nextDue(Frequency.QUARTERLY, base, null)).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(BenefitRules.nextDue(Frequency.MONTHLY, base, LocalDate.of(2026, 7, 15)))
                .isEqualTo(LocalDate.of(2026, 8, 15));
        assertThat(BenefitRules.nextDue(Frequency.ANNUAL, base, base.minusDays(1))).isEqualTo(LocalDate.of(2027, 1, 1));
        assertThat(BenefitRules.realisedMinor(1_000_000L, d("42.5"))).isEqualTo(425_000L);
        assertThat(BenefitRules.realisedMinor(1_000_000L, d("130"))).isEqualTo(1_000_000L);
        assertThat(BenefitRules.realisedMinor(1_000_000L, d("-10"))).isEqualTo(0L);
        assertThat(BenefitRules.realisedMinor(null, d("50"))).isEqualTo(0L);
    }
}
