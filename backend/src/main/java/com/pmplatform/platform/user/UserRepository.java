package com.pmplatform.platform.user;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByTenantIdAndId(UUID tenantId, UUID id);

    Optional<User> findByTenantIdAndExternalSubject(UUID tenantId, String externalSubject);

    List<User> findByTenantIdAndActiveTrueOrderByDisplayNameAsc(UUID tenantId);

    List<User> findByTenantIdAndIdIn(UUID tenantId, Collection<UUID> ids);

    @Query("select distinct u from User u join u.roles r "
            + "where u.tenantId = :tenantId and u.active = true and r = :role")
    List<User> findActiveWithRole(@Param("tenantId") UUID tenantId, @Param("role") String role);

    List<User> findByTenantIdOrderByDisplayNameAsc(UUID tenantId);

    @Query("select u from User u where u.tenantId = :tenantId and lower(u.email) = lower(:email)")
    Optional<User> findByTenantIdAndEmail(@Param("tenantId") UUID tenantId, @Param("email") String email);

    /** Whether an active person with this email is waiting to be linked (no entity loaded). */
    @Query("select count(u) > 0 from User u where u.tenantId = :tenantId and lower(u.email) = lower(:email) "
            + "and u.externalSubject is null and u.active = true")
    boolean existsUnlinked(@Param("tenantId") UUID tenantId, @Param("email") String email);

    /**
     * First sign-in: records the identity-provider subject on the active, not-yet-linked person with
     * this email. Returns 1 if linked, 0 if there is no such person (or someone else linked first).
     */
    @Modifying
    @Query("update User u set u.externalSubject = :subject where u.tenantId = :tenantId "
            + "and lower(u.email) = lower(:email) and u.externalSubject is null and u.active = true")
    int linkSubject(@Param("tenantId") UUID tenantId, @Param("email") String email,
            @Param("subject") String subject);

    @Query(value = "select distinct r.role_code from platform.user_roles r "
            + "join platform.users u on u.id = r.user_id where u.tenant_id = :tenantId", nativeQuery = true)
    List<String> roleCodesInUse(@Param("tenantId") UUID tenantId);
}
