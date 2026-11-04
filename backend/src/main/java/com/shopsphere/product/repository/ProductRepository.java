package com.shopsphere.product.repository;

import java.math.BigDecimal;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.shopsphere.product.entity.Product;

public interface ProductRepository extends JpaRepository<Product, Long> {

    /** Catalogue listing with all trader-customer filters in one query. */
    @Query("""
            SELECT p FROM Product p
            JOIN p.category c
            WHERE p.isDeleted = false AND p.isActive = true
              AND (:search IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%')))
              AND (:minPrice IS NULL OR p.sellingPrice >= :minPrice)
              AND (:maxPrice IS NULL OR p.sellingPrice <= :maxPrice)
              AND (:categoryId IS NULL OR c.id = :categoryId
                   OR c.parent.id = :categoryId)
              AND (:brandId IS NULL OR p.brand.id = :brandId)
              AND (:minRating IS NULL OR p.ratingAvg >= :minRating)
              AND (:inStock IS NULL OR :inStock = false OR (
                   p.inventory IS NOT NULL AND (p.inventory.totalQuantity - p.inventory.reservedQuantity) > 0))
            """)
    Page<Product> searchCatalogue(
            @Param("search") String search,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            @Param("categoryId") Long categoryId,
            @Param("brandId") Long brandId,
            @Param("minRating") Double minRating,
            @Param("inStock") Boolean inStock,
            Pageable pageable);

    @Query("""
            SELECT p FROM Product p
            WHERE p.isDeleted = false AND p.isActive = true
              AND p.category.id = :categoryId
              AND p.id <> :excludeId
            ORDER BY p.ratingAvg DESC
            """)
    Page<Product> findRelated(@Param("categoryId") Long categoryId,
                              @Param("excludeId") Long excludeId,
                              Pageable pageable);

    // Featured + best sellers + new arrivals
    Page<Product> findByIsFeaturedTrueAndIsActiveTrueAndIsDeletedFalse(Pageable pageable);
    Page<Product> findByIsActiveTrueAndIsDeletedFalseOrderByCreatedAtDesc(Pageable pageable);
    Page<Product> findByIsActiveTrueAndIsDeletedFalseOrderByRatingCountDesc(Pageable pageable);

    Optional<Product> findByIdAndIsDeletedFalse(Long id);

    Optional<Product> findBySlugAndIsDeletedFalse(String slug);

    Page<Product> findByNameContainingIgnoreCaseAndIsActiveTrueAndIsDeletedFalse(String name, Pageable pageable);

    // ------------------------------------------------------------ admin
    /** Includes inactive/deleted for the admin grid, with search. */
    @Query("""
            SELECT p FROM Product p
            WHERE (:search IS NULL
                   OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(p.slug) LIKE LOWER(CONCAT('%', :search, '%')))
            ORDER BY p.createdAt DESC
            """)
    Page<Product> searchAdmin(@Param("search") String search, Pageable pageable);

    long countByIsDeletedFalse();
    long countByIsActiveTrueAndIsDeletedFalse();
    long countByRatingAvgGreaterThanEqualAndIsDeletedFalse(BigDecimal minRating);
}
