package com.shopsphere.category.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopsphere.category.entity.Category;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<Category> findByIsActiveTrueOrderByParentAscNameAsc();
    List<Category> findByParentIsNullAndIsActiveTrueOrderByNameAsc();
    List<Category> findByParentId(Long parentId);
    Optional<Category> findBySlug(String slug);
    boolean existsBySlug(String slug);
    long countByParentIdAndIsActiveTrue(Long parentId);
}
