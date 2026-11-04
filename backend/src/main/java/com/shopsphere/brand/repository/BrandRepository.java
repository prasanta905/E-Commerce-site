package com.shopsphere.brand.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopsphere.brand.entity.Brand;

public interface BrandRepository extends JpaRepository<Brand, Long> {
    List<Brand> findByIsActiveTrueOrderByNameAsc();
    Optional<Brand> findBySlug(String slug);
    boolean existsBySlug(String slug);
}
