package com.pmplatform.platform.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Two security modes.
 *
 * <ul>
 *   <li>Default (staging, production): stateless OAuth2 resource server. Every
 *       {@code /api/**} call needs a JWT from the identity provider (Keycloak),
 *       configured with {@code PMP_OIDC_ISSUER_URI}.</li>
 *   <li>{@code dev} profile: API open for local work, tenant taken from the
 *       {@code X-Tenant-ID} header. Never enable this profile on a server.</li>
 *   <li>{@code dev,sso}: the development sample data, but signing in through the local Keycloak
 *       ({@code docker compose --profile sso up -d}), as on a server.</li>
 * </ul>
 */
@Configuration
public class SecurityConfig {

    @Bean
    @Profile("!dev | sso")
    SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()));
        return http.build();
    }

    @Bean
    @Profile("dev & !sso")
    SecurityFilterChain devSecurity(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
