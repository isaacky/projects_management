package com.pmplatform.benefits;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Pure rules for benefits: progress towards target, where it should be by now, and when it is next due. */
public final class BenefitRules {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    /** Progress is capped at +/- a million per cent: beyond that a reading is almost surely a typo. */
    private static final BigDecimal LIMIT = BigDecimal.valueOf(1_000_000);

    private BenefitRules() {
    }

    /** The direction the target implies; INCREASE when the target is above the baseline. */
    public static Direction direction(BigDecimal baseline, BigDecimal target) {
        return target.compareTo(baseline) > 0 ? Direction.INCREASE : Direction.DECREASE;
    }

    /**
     * Share of the way from baseline to target, as a percentage (2 dp). Can be below 0 (moved the
     * wrong way) or above 100 (beyond target).
     */
    public static BigDecimal progressPct(BigDecimal baseline, BigDecimal target, BigDecimal actual) {
        BigDecimal span = target.subtract(baseline);
        if (span.signum() == 0) {
            return HUNDRED;
        }
        return actual.subtract(baseline).multiply(HUNDRED).divide(span, 2, RoundingMode.HALF_UP)
                .max(LIMIT.negate()).min(LIMIT);
    }

    /** Where progress should be on the given day, on a straight line from baseline date to target date. */
    public static BigDecimal expectedPct(LocalDate baselineDate, LocalDate targetDate, LocalDate asOf) {
        long total = ChronoUnit.DAYS.between(baselineDate, targetDate);
        if (total <= 0 || !asOf.isBefore(targetDate)) {
            return HUNDRED;
        }
        if (!asOf.isAfter(baselineDate)) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(ChronoUnit.DAYS.between(baselineDate, asOf)).multiply(HUNDRED)
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }

    /**
     * ACHIEVED at or beyond target; otherwise ON_TRACK when progress is at least 90% of where it
     * should be by now, AT_RISK from 60%, OFF_TRACK below. Null when nothing has been measured.
     */
    public static BenefitRating rating(BigDecimal progress, BigDecimal expected) {
        if (progress == null) {
            return null;
        }
        if (progress.compareTo(HUNDRED) >= 0) {
            return BenefitRating.ACHIEVED;
        }
        if (expected.signum() <= 0) {
            return progress.signum() >= 0 ? BenefitRating.ON_TRACK : BenefitRating.OFF_TRACK;
        }
        BigDecimal ratio = progress.divide(expected, 4, RoundingMode.HALF_UP);
        if (ratio.compareTo(new BigDecimal("0.9")) >= 0) {
            return BenefitRating.ON_TRACK;
        }
        return ratio.compareTo(new BigDecimal("0.6")) >= 0 ? BenefitRating.AT_RISK : BenefitRating.OFF_TRACK;
    }

    /** The next measurement is due one period after the last one (or after the baseline date). */
    public static LocalDate nextDue(Frequency frequency, LocalDate baselineDate, LocalDate lastMeasuredOn) {
        LocalDate from = lastMeasuredOn != null && lastMeasuredOn.isAfter(baselineDate) ? lastMeasuredOn : baselineDate;
        return from.plusMonths(frequency.months());
    }

    /** Money realised so far: the yearly value scaled by progress (0-100%). */
    public static long realisedMinor(Long annualValueMinor, BigDecimal progress) {
        if (annualValueMinor == null || progress == null) {
            return 0;
        }
        BigDecimal p = progress.max(BigDecimal.ZERO).min(HUNDRED);
        return BigDecimal.valueOf(annualValueMinor).multiply(p).divide(HUNDRED, 0, RoundingMode.HALF_UP).longValue();
    }
}
