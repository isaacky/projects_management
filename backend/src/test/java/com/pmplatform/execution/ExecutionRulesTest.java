package com.pmplatform.execution;

import static org.assertj.core.api.Assertions.assertThat;

import com.pmplatform.execution.ExecutionRules.RiskLevel;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ExecutionRulesTest {

    @Test
    void riskLevelsFollowTheFiveByFiveMatrix() {
        assertThat(ExecutionRules.level(1, 4)).isEqualTo(RiskLevel.LOW);
        assertThat(ExecutionRules.level(1, 5)).isEqualTo(RiskLevel.MEDIUM);
        assertThat(ExecutionRules.level(3, 3)).isEqualTo(RiskLevel.MEDIUM);
        assertThat(ExecutionRules.level(2, 5)).isEqualTo(RiskLevel.HIGH);
        assertThat(ExecutionRules.level(4, 4)).isEqualTo(RiskLevel.HIGH);
        assertThat(ExecutionRules.level(4, 5)).isEqualTo(RiskLevel.CRITICAL);
    }

    @Test
    void suggestedRatings() {
        assertThat(ExecutionRules.scheduleRag(new BigDecimal("1.02"), 0)).isEqualTo(Rag.GREEN);
        assertThat(ExecutionRules.scheduleRag(new BigDecimal("0.90"), 0)).isEqualTo(Rag.AMBER);
        assertThat(ExecutionRules.scheduleRag(new BigDecimal("1.00"), 5)).isEqualTo(Rag.AMBER);
        assertThat(ExecutionRules.scheduleRag(new BigDecimal("0.80"), 0)).isEqualTo(Rag.RED);
        assertThat(ExecutionRules.scheduleRag(null, 45)).isEqualTo(Rag.RED);
        assertThat(ExecutionRules.costRag(null, 90, 100)).isEqualTo(Rag.GREEN);
        assertThat(ExecutionRules.costRag(new BigDecimal("0.9"), 90, 100)).isEqualTo(Rag.AMBER);
        assertThat(ExecutionRules.costRag(new BigDecimal("1.1"), 120, 100)).isEqualTo(Rag.RED);
        assertThat(ExecutionRules.riskRag(0, 2)).isEqualTo(Rag.AMBER);
        assertThat(ExecutionRules.riskRag(1, 0)).isEqualTo(Rag.RED);
        assertThat(ExecutionRules.worst(Rag.GREEN, Rag.AMBER, Rag.GREEN)).isEqualTo(Rag.AMBER);
    }

    @Test
    void costPerformanceIndex() {
        // 40% earned of a 1,000,000 budget = 400,000 earned value; 500,000 certified -> 0.8
        assertThat(ExecutionRules.cpi(new BigDecimal("40"), 1_000_000, 500_000)).isEqualByComparingTo("0.800");
        assertThat(ExecutionRules.cpi(new BigDecimal("40"), 1_000_000, 0)).isNull();
    }
}
