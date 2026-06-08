package com.demo.upimesh.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * UPI Offline Mesh — API Gateway
 *
 * <p>Single edge entry point for all client traffic. Responsibilities:
 * <ol>
 *   <li>JWT authentication — the {@code JwtAuthenticationFilter} validates every
 *       token before the request reaches a downstream service.</li>
 *   <li>Role-based authorization — mesh status endpoints require ROLE_USER;
 *       bridge flush/ingest endpoints require ROLE_BRIDGE.</li>
 *   <li>Load-balanced routing — {@code lb://mesh-service} and
 *       {@code lb://payment-service} are resolved via the Eureka registry.</li>
 * </ol>
 *
 * <p>Runs on Netty (reactive / non-blocking) — do NOT add spring-boot-starter-web.
 */
@SpringBootApplication
@EnableDiscoveryClient
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
