package com.pmplatform.portfolio;

import java.util.List;
import java.util.UUID;

/**
 * Lets other contexts hold a project at a gate until their own conditions are met (for example,
 * closeout holds gate G4 until the handover is accepted). Implementations must not depend on
 * {@link ProjectService}.
 */
public interface PhaseGateCheck {

    /** What still stops the project leaving its current phase; empty when nothing does. */
    List<String> blockers(UUID tenantId, Project project);
}
