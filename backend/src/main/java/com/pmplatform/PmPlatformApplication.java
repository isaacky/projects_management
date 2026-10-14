package com.pmplatform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Modular monolith entry point.
 *
 * <p>Each top-level package below {@code com.pmplatform} is a bounded context
 * (platform, portfolio, procurement, contracts, planning, execution, closeout,
 * benefits). Contexts own their own database schema and talk to each other only
 * through public service interfaces or domain events.
 */
@SpringBootApplication
public class PmPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(PmPlatformApplication.class, args);
    }
}
