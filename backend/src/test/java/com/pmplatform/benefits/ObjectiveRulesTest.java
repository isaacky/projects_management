package com.pmplatform.benefits;

import static com.pmplatform.portfolio.ObjectiveOutcome.MET;
import static com.pmplatform.portfolio.ObjectiveOutcome.NOT_MET;
import static com.pmplatform.portfolio.ObjectiveOutcome.PARTLY_MET;
import static com.pmplatform.portfolio.ObjectiveOutcome.TOO_EARLY;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ObjectiveRulesTest {

    @Test
    void everyDecidedObjectiveMetIsYes() {
        assertThat(ObjectiveRules.overall(List.of(MET, MET))).isEqualTo(ObjectivesMet.YES);
        assertThat(ObjectiveRules.overall(List.of(MET, TOO_EARLY))).isEqualTo(ObjectivesMet.YES);
    }

    @Test
    void noneMetIsNo() {
        assertThat(ObjectiveRules.overall(List.of(NOT_MET, NOT_MET, TOO_EARLY))).isEqualTo(ObjectivesMet.NO);
    }

    @Test
    void anythingInBetweenIsPartly() {
        assertThat(ObjectiveRules.overall(List.of(MET, NOT_MET))).isEqualTo(ObjectivesMet.PARTLY);
        assertThat(ObjectiveRules.overall(List.of(PARTLY_MET))).isEqualTo(ObjectivesMet.PARTLY);
        assertThat(ObjectiveRules.overall(List.of(NOT_MET, PARTLY_MET))).isEqualTo(ObjectivesMet.PARTLY);
    }

    @Test
    void nothingDecidedLeavesItToTheReviewer() {
        assertThat(ObjectiveRules.overall(List.of())).isNull();
        assertThat(ObjectiveRules.overall(List.of(TOO_EARLY, TOO_EARLY))).isNull();
    }
}
