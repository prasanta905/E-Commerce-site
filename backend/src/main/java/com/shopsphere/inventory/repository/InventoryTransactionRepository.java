package com.shopsphere.inventory.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopsphere.inventory.entity.InventoryTransaction;

public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, Long> {
    List<InventoryTransaction> findByInventoryIdOrderByCreatedAtDesc(Long inventoryId);
    List<InventoryTransaction> findByInventory_Product_IdOrderByCreatedAtDesc(Long productId);
}
