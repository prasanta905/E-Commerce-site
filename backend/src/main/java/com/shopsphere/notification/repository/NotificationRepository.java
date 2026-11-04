package com.shopsphere.notification.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.shopsphere.notification.entity.Notification;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    @Query("""
            SELECT n FROM Notification n
            WHERE n.user.id = :userId
            ORDER BY n.createdAt DESC
            """)
    java.util.List<Notification> findByUserIdOrderByCreatedAtDesc(@Param("userId") Long userId);

    long countByUserIdAndReadAtIsNull(Long userId);
}
