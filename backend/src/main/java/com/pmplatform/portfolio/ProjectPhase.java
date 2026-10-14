package com.pmplatform.portfolio;

import java.util.Optional;

/**
 * The six lifecycle phases. A project moves forward one phase at a time, and only
 * through the gate that closes its current phase.
 */
public enum ProjectPhase {
    INITIATION("G0", "Approval to procure"),
    PROCUREMENT("G1", "Contract award approved"),
    PLANNING("G2", "Baseline approved"),
    EXECUTION("G3", "Practical completion"),
    CLOSEOUT("G4", "Formal closure"),
    IMPLEMENTATION(null, null);

    private final String exitGate;
    private final String exitGateName;

    ProjectPhase(String exitGate, String exitGateName) {
        this.exitGate = exitGate;
        this.exitGateName = exitGateName;
    }

    public Optional<String> exitGate() {
        return Optional.ofNullable(exitGate);
    }

    public Optional<String> exitGateName() {
        return Optional.ofNullable(exitGateName);
    }

    public Optional<ProjectPhase> next() {
        ProjectPhase[] all = values();
        return ordinal() + 1 < all.length ? Optional.of(all[ordinal() + 1]) : Optional.empty();
    }
}
