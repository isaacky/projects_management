package com.pmplatform.procurement.supplier;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplierRepository extends JpaRepository<Supplier, UUID> {

    List<Supplier> findByTenantIdOrderByNameAsc(UUID tenantId);

    Optional<Supplier> findByTenantIdAndId(UUID tenantId, UUID id);

    Optional<Supplier> findByTenantIdAndKraPin(UUID tenantId, String kraPin);

    List<Supplier> findByTenantIdAndKraPinIn(UUID tenantId, Collection<String> kraPins);

    List<Supplier> findByTenantIdAndIdIn(UUID tenantId, Collection<UUID> ids);
}
