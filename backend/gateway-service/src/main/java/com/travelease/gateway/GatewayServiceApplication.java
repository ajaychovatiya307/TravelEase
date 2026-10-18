package com.travelease.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Single public HTTP entry point for the TravelEase frontend.
 *
 * <p>The gateway deliberately does not reinterpret domain payloads. It forwards
 * the documented {@code /api/**} contract, including the caller's bearer token,
 * to the service that owns each resource.</p>
 */
@SpringBootApplication
public class GatewayServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayServiceApplication.class, args);
    }
}
