package com.pmplatform.procurement.evaluation;

public enum CommitteeRole {
    CHAIR,
    MEMBER,
    /** Records the proceedings; does not score or count towards the quorum. */
    SECRETARY;

    public boolean isVoting() {
        return this != SECRETARY;
    }
}
