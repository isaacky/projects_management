package com.pmplatform.contracts;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SecurityRepository extends JpaRepository<Security, UUID> {

    List<Security> findByContractIdOrderByReceivedAtAsc(UUID contractId);

    /** One tenant's held securities expiring on or before the date that have not been notified yet. */
    @Query("select s from Security s where s.status = :status and s.expiryDate <= :date "
            + "and s.expiryNotifiedAt is null "
            + "and s.contractId in (select c.id from Contract c where c.tenantId = :tenantId)")
    List<Security> findExpiringUnnotified(@Param("tenantId") UUID tenantId, @Param("status") SecurityStatus status,
            @Param("date") LocalDate date);
}
