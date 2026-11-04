package com.shopsphere.review.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.shopsphere.review.entity.Review;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    Page<Review> findByProductIdOrderByCreatedAtDesc(Long productId, Pageable pageable);

    Optional<Review> findByIdAndProductId(Long id, Long productId);

    boolean existsByProductIdAndUserId(Long productId, Long userId);

    @Query("""
            SELECT AVG(r.rating), COUNT(r) FROM Review r
            WHERE r.product.id = :productId
            """)
    RatingAggregate aggregateForProduct(@Param("productId") Long productId);

    interface RatingAggregate {
        Double getAvg();
        Long getCount();
    }
}
