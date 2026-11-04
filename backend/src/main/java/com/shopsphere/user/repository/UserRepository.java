package com.shopsphere.user.repository;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.shopsphere.user.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCaseAndIsDeletedFalse(String email);

    boolean existsByEmailIgnoreCaseAndIsDeletedFalse(String email);

    // ------------------------------------------------------------ admin
    @Query("""
            SELECT u FROM User u
            WHERE u.isDeleted = false
              AND (:q IS NULL
                   OR LOWER(u.email) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :q, '%')))
              AND (:active IS NULL OR u.isActive = :active)
            ORDER BY u.createdAt DESC
            """)
    Page<User> searchAdmin(@Param("q") String q, @Param("active") Boolean active, Pageable pageable);

    long countByIsDeletedFalse();

    long countByIsDeletedFalseAndIsActiveTrue();

    long countByIsDeletedFalseAndCreatedAtGreaterThanEqual(java.time.Instant since);
}
