package com.pmplatform.procurement.plan;

/** How far a plan line has got, from its requisitions and tenders. */
public enum LineStage {
    NOT_STARTED,
    REQUISITIONED,
    TENDERING,
    AWARDED
}
