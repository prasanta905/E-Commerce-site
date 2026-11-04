package com.shopsphere;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 2 smoke: proves the Spring context starts, datasource pool connects,
 * Flyway runs cleanly even with an empty migration set, and web server beans are present.
 * Runs against the local `shopsphere` MySQL schema (application-local provider).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
class ShopSphereApplicationTests {

    @Test
    void contextBootsWithLocalDatabase(ApplicationContext context) {
        assertThat(context).isNotNull();
        // Phase 3 will strengthen: assert Flyway ran and tables/migrations exist.
    }
}
