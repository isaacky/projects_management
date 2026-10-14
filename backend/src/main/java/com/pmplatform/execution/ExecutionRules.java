package com.pmplatform.execution;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Scoring and suggested RAG ratings. Pure functions. */
public final class ExecutionRules {

    /** Performance indices are capped here; early in a project they can be huge and mean little. */
    public static final BigDecimal INDEX_CAP = new BigDecimal("99.999");

    private ExecutionRules() {
    }

    public enum RiskLevel { LOW, MEDIUM, HIGH, CRITICAL }

    /** 5x5 matrix: score = probability x impact. 1-4 low, 5-9 medium, 10-16 high, 20-25 critical. */
    public static RiskLevel level(int probability, int impact) {
        int score = probability * impact;
        return score >= 20 ? RiskLevel.CRITICAL : score >= 10 ? RiskLevel.HIGH : score >= 5 ? RiskLevel.MEDIUM
                : RiskLevel.LOW;
    }

    /** Schedule: green when on plan (SPI 0.95+ and no slip), red when SPI is below 0.85 or the slip exceeds 30 days. */
    public static Rag scheduleRag(BigDecimal spi, int slipDays) {
        if ((spi != null && spi.compareTo(new BigDecimal("0.85")) < 0) || slipDays > 30) {
            return Rag.RED;
        }
        if ((spi != null && spi.compareTo(new BigDecimal("0.95")) < 0) || slipDays > 0) {
            return Rag.AMBER;
        }
        return Rag.GREEN;
    }

    /** Cost: from CPI (green 0.95+, amber 0.85+), and red when commitments exceed the budget. */
    public static Rag costRag(BigDecimal cpi, long committedMinor, long budgetMinor) {
        if (budgetMinor > 0 && committedMinor > budgetMinor) {
            return Rag.RED;
        }
        if (cpi == null) {
            return Rag.GREEN;
        }
        return cpi.compareTo(new BigDecimal("0.95")) >= 0 ? Rag.GREEN
                : cpi.compareTo(new BigDecimal("0.85")) >= 0 ? Rag.AMBER : Rag.RED;
    }

    public static Rag riskRag(long criticalOpen, long highOpen) {
        return criticalOpen > 0 ? Rag.RED : highOpen > 0 ? Rag.AMBER : Rag.GREEN;
    }

    /** The worst of the ratings. */
    public static Rag worst(Rag... rags) {
        Rag w = Rag.GREEN;
        for (Rag r : rags) {
            if (r.ordinal() > w.ordinal()) {
                w = r;
            }
        }
        return w;
    }

    /**
     * Cost performance index: earned value (earned % of the budget) over actual cost (certified
     * contract payments). Null when nothing is certified yet.
     */
    public static BigDecimal cpi(BigDecimal earnedPct, long budgetMinor, long certifiedMinor) {
        if (certifiedMinor <= 0 || earnedPct == null) {
            return null;
        }
        BigDecimal ev = earnedPct.multiply(BigDecimal.valueOf(budgetMinor)).divide(BigDecimal.valueOf(100), 2,
                RoundingMode.HALF_UP);
        return ev.divide(BigDecimal.valueOf(certifiedMinor), 3, RoundingMode.HALF_UP).min(INDEX_CAP);
    }
}
