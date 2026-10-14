package com.pmplatform.benefits;

import com.pmplatform.platform.tenant.TenantContext;
import com.pmplatform.platform.tenant.TenantDirectory;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Every morning, reminds benefit owners of measurements due and review leads of reviews within a week.
 * Works one tenant at a time (row-level security shows a connection only its own tenant's rows).
 */
@Component
public class BenefitReminderJob {

    private static final Logger LOG = LoggerFactory.getLogger(BenefitReminderJob.class);

    private final BenefitService benefits;
    private final TenantDirectory tenants;

    public BenefitReminderJob(BenefitService benefits, TenantDirectory tenants) {
        this.benefits = benefits;
        this.tenants = tenants;
    }

    @Scheduled(cron = "${pmp.benefits.reminder-cron:0 15 7 * * *}", zone = "Africa/Nairobi")
    public void run() {
        int sent = 0;
        for (UUID tenantId : tenants.activeTenantIds()) {
            try {
                // one transaction per tenant, opened after the tenant is set
                sent += TenantContext.callAs(tenantId, () -> benefits.sendReminders(tenantId));
            } catch (RuntimeException e) {
                LOG.warn("Benefit reminders failed for tenant {}: {}", tenantId, e.getMessage());
            }
        }
        if (sent > 0) {
            LOG.info("Sent {} benefit reminders", sent);
        }
    }
}
