package com.shopsphere.inventory.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.shopsphere.inventory.entity.Inventory;
import jakarta.persistence.LockModeType;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    Optional<Inventory> findByProductId(Long productId);

    /** Row-locked read inside the checkout transaction (pessimistic). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventory i WHERE i.product.id = :productId")
    Optional<Inventory> findByProductIdForUpdate(@Param("productId") Long productId);

    /**
     * Reservation increment guarded by available stock at the SQL level so even
     * racers cannot oversell.
     */
    @Modifying
    @Query("""
            UPDATE Inventory i
            SET i.reservedQuantity = i.reservedQuantity + :qty
            WHERE i.product.id = :productId
              AND (i.totalQuantity - i.reservedQuantity) >= :qty
            """)
    int reserve(@Param("productId") Long productId, @Param("qty") int qty);

    @Modifying
    @Query("""
            UPDATE Inventory i
            SET i.reservedQuantity = i.reservedQuantity - :qty
            WHERE i.product.id = :productId AND i.reservedQuantity >= :qty
            """)
    int release(@Param("productId") Long productId, @Param("qty") int qty);

    /** Deduct after payment confirmation: burn one reserved unit & one stock unit. */
    @Modifying
    @Query("""
            UPDATE Inventory i
            SET i.totalQuantity = i.totalQuantity - :qty, i.reservedQuantity = i.reservedQuantity - :qty
            WHERE i.product.id = :productId AND i.reservedQuantity >= :qty AND i.totalQuantity >= :qty
            """)
    int commitReservation(@Param("productId") Long productId, @Param("qty") int qty);

    /** Direct stock consume (COD path uses reserve → commit equivalently). */
    @Modifying
    @Query("""
            UPDATE Inventory i
            SET i.totalQuantity = i.totalQuantity - :qty
            WHERE i.product.id = :productId AND (i.totalQuantity - i.reservedQuantity) >= :qty
            """)
    int consume(@Param("productId") Long productId, @Param("qty") int qty);

    @Modifying
    @Query("UPDATE Inventory i SET i.totalQuantity = i.totalQuantity + :qty WHERE i.product.id = :productId")
    int restock(@Param("productId") Long productId, @Param("qty") int qty);

    /** Products whose available stock ≤ their low-stock threshold. */
    @Query("""
            SELECT i FROM Inventory i
            WHERE (i.totalQuantity - i.reservedQuantity) <= i.lowStockThreshold
            ORDER BY (i.totalQuantity - i.reservedQuantity) ASC
            """)
    List<Inventory> findLowStock();
}
