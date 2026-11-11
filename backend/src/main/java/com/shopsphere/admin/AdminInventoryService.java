package com.shopsphere.admin;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopsphere.common.exception.BadRequest;
import com.shopsphere.common.exception.ResourceNotFound;
import com.shopsphere.inventory.entity.Inventory;
import com.shopsphere.inventory.entity.InventoryTransaction;
import com.shopsphere.inventory.entity.InventoryTransactionType;
import com.shopsphere.inventory.repository.InventoryRepository;
import com.shopsphere.inventory.repository.InventoryTransactionRepository;
import com.shopsphere.notification.NotificationSaver;
import com.shopsphere.product.entity.Product;
import com.shopsphere.product.repository.ProductRepository;
import com.shopsphere.user.repository.UserRepository;

/**
 * Inventory adjustment endpoint — every delta is audited into
 * inventory_transactions with the admin's reason.
 */
@Service
public class AdminInventoryService {

    private final InventoryRepository inventoryRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final NotificationSaver notificationSaver;

    public AdminInventoryService(InventoryRepository inventoryRepository,
                                 InventoryTransactionRepository inventoryTransactionRepository,
                                 ProductRepository productRepository,
                                 UserRepository userRepository,
                                 NotificationSaver notificationSaver) {
        this.inventoryRepository = inventoryRepository;
        this.inventoryTransactionRepository = inventoryTransactionRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.notificationSaver = notificationSaver;
    }

    public record AdminInventoryRow(Long productId, String productName, String sku,
                                    int total, int reserved, int available,
                                    int lowStockThreshold, boolean lowStock) {}

    public record AdjustRequest(int delta, String reason) {}

    public record AdjustResponse(Long productId, String productName, int totalQuantity,
                                 int availableQuantity, boolean lowStock, String message) {}

    @Transactional(readOnly = true)
    public List<AdminInventoryRow> list(boolean lowOnly) {
        List<Inventory> rows = lowOnly ? inventoryRepository.findLowStock()
                : inventoryRepository.findAll();
        return rows.stream()
                .sorted(java.util.Comparator.comparingInt(Inventory::availableQuantity))
                .map(this::toRow)
                .toList();
    }

    @Transactional
    public AdjustResponse adjust(Long productId, AdjustRequest request) {
        if (request.delta() == 0) {
            throw BadRequest.of("DELTA_ZERO", "Delta must be non-zero");
        }
        if (request.reason() == null || request.reason().isBlank()) {
            throw BadRequest.of("REASON_REQUIRED", "An audit reason is required for stock adjustments");
        }
        Product product = productRepository.findById(productId)
                .filter(p -> Boolean.FALSE.equals(p.getIsDeleted()))
                .orElseThrow(() -> ResourceNotFound.of("Product"));
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseGet(() -> {
                    Inventory created = Inventory.builder().product(product).totalQuantity(0).build();
                    product.setInventory(created);
                    return inventoryRepository.save(created);
                });

        int newTotal = inventory.getTotalQuantity() + request.delta();
        if (newTotal < 0 || inventory.availableQuantity() + request.delta() < 0) {
            throw BadRequest.of("STOCK_CONFLICT",
                    "Adjustment would drop available stock below zero (reserved units are on hold)");
        }
        inventory.setTotalQuantity(newTotal);
        inventoryRepository.save(inventory);

        inventoryTransactionRepository.save(InventoryTransaction.builder()
                .inventory(inventory)
                .type(InventoryTransactionType.ADJUST)
                .quantityDelta(request.delta())
                .note("Admin adjustment: " + request.reason())
                .build());

        if (inventory.isLowStock()) {
            userRepository.findByEmailIgnoreCaseAndIsDeletedFalse("admin@shopsphere.local")
                    .ifPresent(admin -> notificationSaver.stockAlert(admin.getId(),
                            "Low stock — " + product.getName(),
                            "Available stock is " + inventory.availableQuantity() + " (threshold "
                                    + inventory.getLowStockThreshold() + ")."));
        }

        return new AdjustResponse(product.getId(), product.getName(), inventory.getTotalQuantity(),
                inventory.availableQuantity(), inventory.isLowStock(),
                "Stock adjusted by " + request.delta() + " units");
    }

    private AdminInventoryRow toRow(Inventory inventory) {
        Product product = inventory.getProduct();
        return new AdminInventoryRow(product.getId(), product.getName(), product.getSlug(),
                inventory.getTotalQuantity(), inventory.getReservedQuantity(),
                inventory.availableQuantity(), inventory.getLowStockThreshold(),
                inventory.isLowStock());
    }
}
