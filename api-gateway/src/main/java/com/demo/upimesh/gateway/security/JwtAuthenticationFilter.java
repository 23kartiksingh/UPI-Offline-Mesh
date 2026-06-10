package com.demo.upimesh.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Reactive JWT Authentication Filter for Spring Cloud Gateway.
 *
 * <p>Execution order in the reactive pipeline:
 * <pre>
 *   Incoming request
 *       │
 *       ▼
 *   JwtAuthenticationFilter   ← this class (runs BEFORE SecurityWebFilterChain authz)
 *       │   extracts Bearer token, validates signature + expiry, builds Authentication
 *       ▼
 *   ReactiveSecurityContextHolder  ← populated with the validated Authentication
 *       │
 *       ▼
 *   SecurityWebFilterChain rules   ← hasRole("USER") / hasRole("BRIDGE") evaluated here
 *       │
 *       ▼
 *   Downstream microservice (lb://mesh-service, lb://payment-service)
 * </pre>
 *
 * <p><strong>Token contract</strong> — the JWT issued by the auth-service must contain:
 * <pre>
 * {
 *   "sub": "alice@okaxis",
 *   "roles": ["ROLE_USER"],          // or ["ROLE_BRIDGE"] for bridge devices
 *   "iat": 1718000000,
 *   "exp": 1718003600
 * }
 * </pre>
 *
 * <p>If the token is absent the filter passes the exchange through unauthenticated —
 * the {@link SecurityConfig} will then reject it at the authorization layer.
 * If the token is present but invalid (bad signature, expired, malformed) the filter
 * short-circuits with 401 Unauthorized immediately, before hitting the route table.
 */
@Component
public class JwtAuthenticationFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String ROLES_CLAIM = "roles";

    @Value("${security.jwt.secret:THIS_IS_A_TEMPORARY_SECRET_KEY_THAT_IS_EXACTLY_64_BYTES_LONG_FOR_DEV}")
    private String jwtSecretString;

    @Value("${security.jwt.allowed-clock-skew-seconds:30}")
    private long allowedClockSkewSeconds;

    /** Derived from the raw secret string; initialised once at startup. */
    private SecretKey signingKey;

    @PostConstruct
    void initSigningKey() {
        // JJWT 0.12.x requires at least 512 bits (64 bytes) for HS512.
        // Keys.hmacShaKeyFor pads/hashes if the raw bytes are shorter,
        // but production keys MUST be at least 64 chars of high-entropy text.
        this.signingKey = Keys.hmacShaKeyFor(
                jwtSecretString.getBytes(StandardCharsets.UTF_8)
        );
        log.info("JwtAuthenticationFilter initialised — clock-skew tolerance: {}s",
                allowedClockSkewSeconds);
    }

    // -------------------------------------------------------------------------
    // WebFilter contract
    // -------------------------------------------------------------------------

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String token = extractBearerToken(exchange);

        if (token == null) {
            // No Authorization header — pass through; SecurityConfig will block
            // protected routes with 401 if they require authentication.
            return chain.filter(exchange);
        }

        try {
            Claims claims = parseAndValidate(token);
            List<SimpleGrantedAuthority> authorities = extractAuthorities(claims);

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            claims.getSubject(),   // principal = VPA / device-id
                            null,                  // credentials intentionally null
                            authorities
                    );

            log.debug("JWT validated for subject='{}', roles={}",
                    claims.getSubject(), authorities);

            // Propagate authentication into the reactive security context
            return chain.filter(exchange)
                    .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));

        } catch (JwtException ex) {
            // Signature mismatch, expiry, malformed — reject immediately
            log.warn("JWT validation failed [{}]: {}", exchange.getRequest().getPath(), ex.getMessage());
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Extracts the raw token string from the {@code Authorization: Bearer <token>}
     * header. Returns {@code null} if the header is absent or not a Bearer token.
     */
    private String extractBearerToken(ServerWebExchange exchange) {
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            return authHeader.substring(BEARER_PREFIX.length()).strip();
        }
        return null;
    }

    /**
     * Validates signature, expiry (with clock-skew tolerance), and returns the claims.
     * Throws {@link JwtException} on any validation failure.
     */
    private Claims parseAndValidate(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .clockSkewSeconds(allowedClockSkewSeconds)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Maps the {@code "roles"} claim (a JSON array of strings) to Spring Security
     * {@link SimpleGrantedAuthority} objects.
     *
     * <p>Example claim: {@code "roles": ["ROLE_USER"]}
     * results in {@code [SimpleGrantedAuthority("ROLE_USER")]}
     *
     * <p>If the claim is absent the token is treated as having no authorities
     * (all role-protected endpoints will be denied).
     */
    @SuppressWarnings("unchecked")
    private List<SimpleGrantedAuthority> extractAuthorities(Claims claims) {
        Object rawRoles = claims.get(ROLES_CLAIM);
        if (rawRoles instanceof List<?> roleList) {
            return roleList.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
