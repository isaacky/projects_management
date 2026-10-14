package com.pmplatform.platform.tenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import com.pmplatform.platform.user.UserContext;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Resolves the tenant for each request.
 *
 * <ul>
 *   <li>Production: the {@code tenant_id} claim of the authenticated JWT. The
 *       header is ignored so a user cannot switch tenants.</li>
 *   <li>Development ({@code pmp.tenant.allow-header=true}): the {@code X-Tenant-ID}
 *       header, so the API can be exercised without an identity provider, falling back
 *       to {@code pmp.tenant.dev-default-id} (the sample tenant) when the header is absent.</li>
 * </ul>
 *
 * Registered as a plain servlet filter, so it runs after Spring Security has
 * populated the security context.
 */
@Component
public class TenantFilter extends OncePerRequestFilter {

    public static final String TENANT_HEADER = "X-Tenant-ID";
    public static final String TENANT_CLAIM = "tenant_id";
    /** Development only: act as another user (the "Viewing as" switcher). */
    public static final String DEV_USER_HEADER = "X-Dev-User-ID";

    private final boolean allowHeader;
    private final UUID devDefaultTenant;

    /**
     * @param devDefaultTenant development only: the tenant used when a request carries
     *        neither a token nor the header. Ignored unless {@code allowHeader} is true.
     */
    public TenantFilter(@Value("${pmp.tenant.allow-header:false}") boolean allowHeader,
            @Value("${pmp.tenant.dev-default-id:}") String devDefaultTenant) {
        this.allowHeader = allowHeader;
        this.devDefaultTenant = allowHeader ? parse(devDefaultTenant) : null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        try {
            UUID tenant = fromJwt();
            if (tenant == null && allowHeader) {
                tenant = parse(request.getHeader(TENANT_HEADER));
            }
            if (tenant == null) {
                tenant = devDefaultTenant;
            }
            if (tenant != null) {
                TenantContext.set(tenant);
            }
            Jwt jwt = currentJwt();
            UserContext.set(jwt == null ? null : jwt.getSubject(), verifiedEmail(jwt),
                    allowHeader ? parse(request.getHeader(DEV_USER_HEADER)) : null);
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            UserContext.clear();
        }
    }

    private UUID fromJwt() {
        Jwt jwt = currentJwt();
        return jwt == null ? null : parse(jwt.getClaimAsString(TENANT_CLAIM));
    }

    /** The token's email, if the identity provider says it is verified. */
    private static String verifiedEmail(Jwt jwt) {
        if (jwt == null || !Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified"))) {
            return null;
        }
        return jwt.getClaimAsString("email");
    }

    private static Jwt currentJwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof Jwt jwt ? jwt : null;
    }

    private static UUID parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
