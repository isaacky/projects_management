package com.pmplatform.closeout;

import com.pmplatform.contracts.RetentionReleaseCheck;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Retention stays held while the contract has defects that are not put right and checked. */
@Component
public class DefectRetentionCheck implements RetentionReleaseCheck {

    private final DefectRepository defects;
    private final CloseoutLocks locks;

    public DefectRetentionCheck(DefectRepository defects, CloseoutLocks locks) {
        this.defects = defects;
        this.locks = locks;
    }

    @Override
    public List<String> blockers(UUID tenantId, UUID contractId) {
        locks.contract(contractId); // a defect being reported on this contract right now is counted
        long open = defects.countByTenantIdAndContractIdAndStatusIn(tenantId, contractId,
                List.of(DefectStatus.OPEN, DefectStatus.RECTIFIED));
        return open == 0 ? List.of()
                : List.of(open + " defect" + (open == 1 ? " is" : "s are") + " still open on this contract (project "
                        + "Closeout tab)");
    }
}
