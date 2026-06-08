package com.demo.upimesh.discovery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * UPI Offline Mesh — Service Discovery Registry
 *
 * <p>Runs a Netflix Eureka server so every microservice (mesh-service,
 * payment-service, api-gateway) can register itself and discover peers
 * without hard-coded hostnames.
 *
 * <p>Key configuration (see application.yml):
 * <ul>
 *   <li>Self-preservation OFF — avoids stale registrations in a local dev cluster.</li>
 *   <li>Self-registration OFF — the registry itself is not a client.</li>
 *   <li>Fetch-registry OFF  — no point fetching its own registry.</li>
 * </ul>
 */
@SpringBootApplication
@EnableEurekaServer
public class DiscoveryServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(DiscoveryServerApplication.class, args);
    }
}
