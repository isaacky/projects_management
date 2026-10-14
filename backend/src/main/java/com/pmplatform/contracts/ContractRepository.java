package com.pmplatform.contracts;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContractRepository extends JpaRepository<Contract, UUID> {

    List<Contract> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<Contract> findByTenantIdAndProjectIdOrderByCreatedAtDesc(UUID tenantId, UUID projectId);

    List<Contract> findByTenantIdAndSupplierIdIn(UUID tenantId, Collection<UUID> supplierIds);

    List<Contract> findByTenantIdAndTenderId(UUID tenantId, UUID tenderId);

    Optional<Contract> findByTenantIdAndId(UUID tenantId, UUID id);

    boolean existsByTenantIdAndTenderIdAndStatusNot(UUID tenantId, UUID tenderId, ContractStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Contract c where c.tenantId = :tenantId and c.id = :id")
    Optional<Contract> lockByTenantIdAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);
}
