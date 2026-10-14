package com.pmplatform.contracts;

import java.util.List;
import java.util.UUID;

/**
 * Lets other contexts hold back the release of retention (for example, closeout while the contract
 * still has defects to put right). Implementations must not depend on {@link ContractService}.
 */
public interface RetentionReleaseCheck {

    /** What still stops retention being released on the contract; empty when nothing does. */
    List<String> blockers(UUID tenantId, UUID contractId);
}
