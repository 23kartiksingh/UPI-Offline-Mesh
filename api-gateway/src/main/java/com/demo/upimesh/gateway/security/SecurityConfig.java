package com.demo.upimesh.gateway.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.HttpStatusServerEntryPoint;
import org.springframework.security.web.server.authorization.HttpStatusServerAccessDeniedHandler;
import reactor.core.publisher.Mono;

/**
 * Reactive Spring Security configuration for the API Gateway.
 *
 * <h3>Authorization matrix (derived from ApiController endpoint inventory)</h3>
 * <pre>
 * ┌──────────────────────────────────────────┬────────────────────────────────┐
 * │ Route pattern                            │ Required authority             │
 * ├──────────────────────────────────────────┼────────────────────────────────┤
 * │ GET  /api/mesh/state                     │ ROLE_USER                      │
 * │ POST /api/mesh/gossip                    │ ROLE_USER                      │
 * │ POST /api/demo/send                      │ ROLE_USER                      │
 * │ GET  /api/accounts                       │ ROLE_USER                      │
 * │ GET  /api/transactions                   │ ROLE_USER                      │
 * │ GET  /api/server-key                     │ ROLE_USER                      │
 * ├──────────────────────────────────────────┼────────────────────────────────┤
 * │ POST /api/mesh/flush   (bridge flush)    │ ROLE_BRIDGE                    │
 * │ POST /api/mesh/reset                     │ ROLE_BRIDGE                    │
 * │ POST /api/bridge/ingest (raw production) │ ROLE_BRIDGE                    │
 * ├──────────────────────────────────────────┼────────────────────────────────┤
 * │ ANY  /api/payment/**                     │ authenticated (any valid role) │
 * ├──────────────────────────────────────────┼────────────────────────────────┤
 * │ GET  /actuator/health                    │ permitAll (liveness probes)    │
 * └──────────────────────────────────────────┴────────────────────────────────┘
 * </pre>
 *
 * <p><strong>Why {@link SecurityWebFiltersOrder#AUTHENTICATION}</strong>:
 * The {@link JwtAuthenticationFilter} must populate the security context
 * <em>before</em> the authorization step runs. Placing it at AUTHENTICATION
 * order guarantees that by the time Spring evaluates {@code hasRole(...)},
 * the {@code Authentication} object is already present.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                // ── CSRF ────────────────────────────────────────────────────
                // Disabled: the gateway is a stateless JWT API — no session,
                // no cookies, so CSRF mitigation is not applicable here.
                .csrf(ServerHttpSecurity.CsrfSpec::disable)

                // ── CORS ─────────────────────────────────────────────────────
                // Delegate to the per-route CORS configuration in application.yml.
                // Remove this line and add a CorsConfigurationSource bean if you
                // need fine-grained control over allowed origins.
                .cors(ServerHttpSecurity.CorsSpec::disable)

                // ── Custom error responses ────────────────────────────────────
                // Return 401 (not a redirect to a login page) for unauthenticated
                // requests, and 403 for authenticated but unauthorized requests.
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(
                                new HttpStatusServerEntryPoint(HttpStatus.UNAUTHORIZED))
                        .accessDeniedHandler(
                                new HttpStatusServerAccessDeniedHandler(HttpStatus.FORBIDDEN))
                )

                // ── JWT filter ────────────────────────────────────────────────
                // Runs before Spring's own AuthenticationWebFilter so that the
                // ReactiveSecurityContextHolder is populated before authz checks.
                .addFilterBefore(jwtAuthenticationFilter, SecurityWebFiltersOrder.AUTHENTICATION)

                // ── Authorization rules ───────────────────────────────────────
                .authorizeExchange(exchanges -> exchanges

                        // ── Open endpoints (no auth required) ──────────────────
                        .pathMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        .pathMatchers(HttpMethod.GET, "/actuator/info").permitAll()
                        // Gateway-level fallback routes (circuit-breaker responses)
                        .pathMatchers("/fallback/**").permitAll()

                        // ── ROLE_BRIDGE — bridge device operations ──────────────
                        // Only physical bridge nodes (phones that have walked into
                        // coverage) carry ROLE_BRIDGE tokens. These endpoints trigger
                        // the actual settlement pipeline.
                        .pathMatchers(HttpMethod.POST, "/api/mesh/flush").hasRole("BRIDGE")
                        .pathMatchers(HttpMethod.POST, "/api/mesh/reset").hasRole("BRIDGE")
                        .pathMatchers(HttpMethod.POST, "/api/bridge/ingest").hasRole("BRIDGE")

                        // ── ROLE_USER — standard user/operator read operations ───
                        // Mesh state monitoring, account views, transaction history.
                        .pathMatchers(HttpMethod.GET, "/api/mesh/state").hasRole("USER")
                        .pathMatchers(HttpMethod.POST, "/api/mesh/gossip").hasRole("USER")
                        .pathMatchers(HttpMethod.POST, "/api/demo/send").hasRole("USER")
                        .pathMatchers(HttpMethod.GET, "/api/server-key").hasRole("USER")
                        .pathMatchers(HttpMethod.GET, "/api/accounts").hasRole("USER")
                        .pathMatchers(HttpMethod.GET, "/api/transactions").hasRole("USER")

                        // ── Payment service — any authenticated principal ─────────
                        // Fine-grained payment authz is enforced inside payment-service.
                        .pathMatchers("/api/payment/**").authenticated()

                        // ── Deny everything else by default ──────────────────────
                        .anyExchange().authenticated()
                )

                // Disable form login and HTTP Basic — this is a pure JWT API
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)

                .build();
    }
}
