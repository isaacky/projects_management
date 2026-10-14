package com.pmplatform.closeout;

import com.pmplatform.portfolio.PhaseGateCheck;
import com.pmplatform.portfolio.Project;
import com.pmplatform.portfolio.ProjectPhase;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Gate G4 (leaving closeout for implementation) needs the handover accepted by the operating unit. */
@Component
public class CloseoutGateCheck implements PhaseGateCheck {

    private final CloseoutRepository closeouts;

    public CloseoutGateCheck(CloseoutRepository closeouts) {
        this.closeouts = closeouts;
    }

    @Override
    public List<String> blockers(UUID tenantId, Project project) {
        if (project.getPhase() != ProjectPhase.CLOSEOUT) {
            return List.of();
        }
        CloseoutStatus status = closeouts.findByTenantIdAndProjectId(tenantId, project.getId())
                .map(Closeout::getStatus).orElse(null);
        if (status == CloseoutStatus.HANDED_OVER || status == CloseoutStatus.CLOSED) {
            return List.of();
        }
        return List.of(status == null ? "closeout has not been started (Closeout tab)"
                : "the handover to the operating unit has not been accepted (Closeout tab)");
    }
}
