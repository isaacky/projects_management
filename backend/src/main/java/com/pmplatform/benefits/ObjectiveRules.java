package com.pmplatform.benefits;

import com.pmplatform.portfolio.ObjectiveOutcome;
import java.util.Collection;

/** How a review's per-objective outcomes add up to its overall "objectives met". */
public final class ObjectiveRules {

    private ObjectiveRules() {
    }

    /**
     * YES when every decided objective was met, NO when none was met even in part, PARTLY otherwise.
     * Objectives judged too early to call are left out; when nothing is decided (no objectives, or all too
     * early) the result is null and the reviewer's own judgement stands.
     */
    public static ObjectivesMet overall(Collection<ObjectiveOutcome> outcomes) {
        long met = 0, notMet = 0, decided = 0;
        for (ObjectiveOutcome o : outcomes) {
            if (o == null || o == ObjectiveOutcome.TOO_EARLY) {
                continue;
            }
            decided++;
            if (o == ObjectiveOutcome.MET) {
                met++;
            } else if (o == ObjectiveOutcome.NOT_MET) {
                notMet++;
            }
        }
        if (decided == 0) {
            return null;
        }
        if (met == decided) {
            return ObjectivesMet.YES;
        }
        return notMet == decided ? ObjectivesMet.NO : ObjectivesMet.PARTLY;
    }
}
