package com.pmplatform.contracts;

import com.pmplatform.platform.tenant.TenantContext;
import com.pmplatform.platform.tenant.TenantDirectory;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Every morning, warns about performance and advance payment securities that expire within 30 days.
 * Works one tenant at a time (row-level security shows a connection only its own tenant's rows).
 */
@Configuration
@EnableScheduling
public class SecurityExpiryJob {

    private static final Logger LOG = LoggerFactory.getLogger(SecurityExpiryJob.class);

    private final ContractService contracts;
    private final TenantDirectory tenants;

    public SecurityExpiryJob(ContractService contracts, TenantDirectory tenants) {
        this.contracts = contracts;
        this.tenants = tenants;
    }

    @Scheduled(cron = "${pmp.contracts.security-check-cron:0 0 7 * * *}", zone = "Africa/Nairobi")
    public void run() {
        int n = 0;
        for (UUID tenantId : tenants.activeTenantIds()) {
            try {
                n += TenantContext.callAs(tenantId, () -> contracts.notifyExpiringSecurities(tenantId));
            } catch (RuntimeException e) {
                LOG.warn("Security expiry check failed for tenant {}: {}", tenantId, e.getMessage());
            }
        }
        if (n > 0) {
            LOG.info("Notified {} expiring contract securities", n);
        }
    }
}
