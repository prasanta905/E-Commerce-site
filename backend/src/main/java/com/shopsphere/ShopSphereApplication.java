package com.shopsphere;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * ShopSphere application entry point.
 * Modular monolith: each feature has its own sub-package (auth, product, order, ...).
 */
@SpringBootApplication
public class ShopSphereApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShopSphereApplication.class, args);
    }
}
