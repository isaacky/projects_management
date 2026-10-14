package com.pmplatform.procurement.plan;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Pure rules for the annual procurement plan (financial-year arithmetic, progress, AGPO share). */
public final class PlanRules {

    private PlanRules() {
    }

    /** "2026/27" for a year starting 1 July 2026; "2027" for a calendar year. */
    public static String label(LocalDate fyStart) {
        if (fyStart.getMonthValue() == 1) {
            return String.valueOf(fyStart.getYear());
        }
        return fyStart.getYear() + "/" + String.format("%02d", (fyStart.getYear() + 1) % 100);
    }

    public static LocalDate fyEnd(LocalDate fyStart) {
        return fyStart.plusYears(1).minusDays(1);
    }

    public static boolean covers(LocalDate fyStart, LocalDate date) {
        return !date.isBefore(fyStart) && !date.isAfter(fyEnd(fyStart));
    }

    /** Quarter 1-4 of the financial year that contains the date, or 0 when outside it. */
    public static int quarterOf(LocalDate fyStart, LocalDate date) {
        if (!covers(fyStart, date)) {
            return 0;
        }
        return (int) (ChronoUnit.MONTHS.between(fyStart, date) / 3) + 1;
    }

    public static LocalDate quarterStart(LocalDate fyStart, int quarter) {
        return fyStart.plusMonths(3L * (quarter - 1));
    }

    public static LocalDate quarterEnd(LocalDate fyStart, int quarter) {
        return fyStart.plusMonths(3L * quarter).minusDays(1);
    }

    public static LineStage stage(long committedMinor, int activeTenders, long awardedMinor) {
        if (awardedMinor > 0) {
            return LineStage.AWARDED;
        }
        if (activeTenders > 0) {
            return LineStage.TENDERING;
        }
        return committedMinor > 0 ? LineStage.REQUISITIONED : LineStage.NOT_STARTED;
    }

    /** An approved line whose quarter has ended without a requisition. */
    public static boolean behind(boolean approved, LineStage stage, LocalDate fyStart, int quarter, LocalDate today) {
        return approved && stage == LineStage.NOT_STARTED && quarterEnd(fyStart, quarter).isBefore(today);
    }

    /** Share of the total, as a percentage with one decimal place. */
    public static BigDecimal pct(long partMinor, long totalMinor) {
        if (totalMinor <= 0) {
            return BigDecimal.ZERO.setScale(1);
        }
        return BigDecimal.valueOf(partMinor).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(totalMinor), 1, RoundingMode.HALF_UP);
    }

    public static long balance(long approvedMinor, long committedMinor) {
        return Math.max(0, approvedMinor - committedMinor);
    }
}
