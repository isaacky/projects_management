package com.pmplatform.contracts;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    List<Invoice> findByContractIdOrderByCertificateNoAsc(UUID contractId);

    List<Invoice> findByContractIdIn(Collection<UUID> contractIds);
}
