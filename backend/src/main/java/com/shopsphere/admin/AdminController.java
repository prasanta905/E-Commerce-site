package com.shopsphere.admin;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.shopsphere.brand.entity.Brand;
import com.shopsphere.brand.repository.BrandRepository;
import com.shopsphere.category.entity.Category;
import com.shopsphere.category.repository.CategoryRepository;
import com.shopsphere.common.ApiResponse;
import com.shopsphere.common.PageResponse;
import com.shopsphere.common.exception.BadRequest;
import com.shopsphere.common.exception.BusinessException;
import com.shopsphere.common.exception.ResourceNotFound;
import com.shopsphere.coupon.entity.Coupon;
import com.shopsphere.coupon.entity.DiscountType;
import com.shopsphere.coupon.repository.CouponRepository;
import com.shopsphere.inventory.entity.Inventory;
import com.shopsphere.inventory.entity.InventoryTransaction;
import com.shopsphere.inventory.entity.InventoryTransactionType;
import com.shopsphere.inventory.repository.InventoryRepository;
import com.shopsphere.inventory.repository.InventoryTransactionRepository;
import com.shopsphere.notification.NotificationSaver;
import com.shopsphere.order.OrderDetailDto;
import com.shopsphere.order.OrderService;
import com.shopsphere.order.OrderSummaryDto;
import com.shopsphere.order.entity.Order;
import com.shopsphere.order.entity.OrderItem;
import com.shopsphere.order.entity.OrderStatus;
import com.shopsphere.order.entity.OrderStatusHistory;
import com.shopsphere.order.entity.ReturnRequest;
import com.shopsphere.order.entity.ReturnStatus;
import com.shopsphere.order.entity.Shipment;
import com.shopsphere.order.repository.OrderRepository;
import com.shopsphere.order.repository.OrderStatusHistoryRepository;
import com.shopsphere.order.repository.ReturnRequestRepository;
import com.shopsphere.order.repository.ShipmentRepository;
import com.shopsphere.payment.entity.Payment;
import com.shopsphere.payment.entity.Refund;
import com.shopsphere.payment.repository.PaymentRepository;
import com.shopsphere.payment.repository.RefundRepository;
import com.shopsphere.product.entity.Product;
import com.shopsphere.product.entity.ProductImage;
import com.shopsphere.product.repository.ProductImageRepository;
import com.shopsphere.product.repository.ProductRepository;
import com.shopsphere.review.entity.Review;
import com.shopsphere.review.repository.ReviewRepository;
import com.shopsphere.security.CurrentUser;
import com.shopsphere.user.entity.User;
import com.shopsphere.user.repository.UserRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * THE admin surface (consolidated). Covers dashboard/analytics, orders +
 * FSM transitions, products/images, categories/brands, customers, inventory
 * with audit, coupons CRUD, returns with refunds and review moderation.
 *
 * Response bodies are DTO projections only — entities never leak.
 */
@Tag(name = "Admin", description = "Admin dashboard, catalog, inventory, order and customer management")
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminDashboardService dashboardService;
    private final AdminProductService adminProductService;
    private final AdminInventoryService adminInventoryService;
    private final OrderService orderService;
    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;
    private final UserRepository userRepository;
    private final CouponRepository couponRepository;
    private final ReturnRequestRepository returnRequestRepository;
    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository statusHistoryRepository;
    private final ShipmentRepository shipmentRepository;
    private final ReviewRepository reviewRepository;
    private final NotificationSaver notificationSaver;
    private final CurrentUser currentUser;

    public AdminController(AdminDashboardService dashboardService,
                           AdminProductService adminProductService,
                           AdminInventoryService adminInventoryService,
                           OrderService orderService,
                           ProductRepository productRepository,
                           ProductImageRepository productImageRepository,
                           CategoryRepository categoryRepository,
                           BrandRepository brandRepository,
                           InventoryRepository inventoryRepository,
                           InventoryTransactionRepository inventoryTransactionRepository,
                           UserRepository userRepository,
                           CouponRepository couponRepository,
                           ReturnRequestRepository returnRequestRepository,
                           PaymentRepository paymentRepository,
                           RefundRepository refundRepository,
                           OrderRepository orderRepository,
                           OrderStatusHistoryRepository statusHistoryRepository,
                           ShipmentRepository shipmentRepository,
                           ReviewRepository reviewRepository,
                           NotificationSaver notificationSaver,
                           CurrentUser currentUser) {
        this.dashboardService = dashboardService;
        this.adminProductService = adminProductService;
        this.adminInventoryService = adminInventoryService;
        this.orderService = orderService;
        this.productRepository = productRepository;
        this.productImageRepository = productImageRepository;
        this.categoryRepository = categoryRepository;
        this.brandRepository = brandRepository;
        this.inventoryRepository = inventoryRepository;
        this.inventoryTransactionRepository = inventoryTransactionRepository;
        this.userRepository = userRepository;
        this.couponRepository = couponRepository;
        this.returnRequestRepository = returnRequestRepository;
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
        this.orderRepository = orderRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.shipmentRepository = shipmentRepository;
        this.reviewRepository = reviewRepository;
        this.notificationSaver = notificationSaver;
        this.currentUser = currentUser;
    }

    // ---------------------------------------------------------------- Dashboard & Analytics
    @Operation(summary = "Admin metrics summary (live queries, no hardcoded numbers)")
    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<AdminDashboardService.DashboardDto>> dashboard() {
        return ResponseEntity.ok(ApiResponse.ok(dashboardService.dashboard()));
    }

    @Operation(summary = "Revenue and sales time-series analytics")
    @GetMapping("/analytics")
    public ResponseEntity<ApiResponse<AdminDashboardService.AnalyticsDto>> analytics(
            @RequestParam(defaultValue = "daily") String period,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        return ResponseEntity.ok(ApiResponse.ok(dashboardService.analytics(period, from, to)));
    }

    // ---------------------------------------------------------------- Orders
    @Operation(summary = "Search and filter orders for admin")
    @GetMapping("/orders")
    public ResponseEntity<ApiResponse<PagedResult<OrderSummaryDto>>> listOrders(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size) {
        Page<OrderSummaryDto> res = orderService.listAllForAdmin(status, search, page, size);
        return ResponseEntity.ok(ApiResponse.ok(new PagedResult<>(res.getContent(), PageResponse.from(res))));
    }

    @Operation(summary = "Order details for admin")
    @GetMapping("/orders/{id}")
    public ResponseEntity<ApiResponse<OrderDetailDto>> getOrderDetail(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.detailForAdmin(id)));
    }

    public record UpdateOrderStatusRequest(@NotNull OrderStatus status, String note,
                                           String courier, String trackingNumber) {}

    @Operation(summary = "Update order status with state machine verification (SHIPPED may carry courier/tracking)")
    @PutMapping("/orders/{id}/status")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, String>>> updateOrderStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateOrderStatusRequest body) {
        if (body.status() == OrderStatus.SHIPPED
                && (body.courier() != null && !body.courier().isBlank()
                    || body.trackingNumber() != null && !body.trackingNumber().isBlank())) {
            upsertShipment(id, body.courier(), body.trackingNumber());
        }
        orderService.transitionStatusByAdmin(id, body.status(), currentUser.require().email(), body.note());
        return ResponseEntity.ok(ApiResponse.ok(Map.of("message", "Order status updated to " + body.status())));
    }

    private void upsertShipment(Long orderId, String courier, String trackingNumber) {
        Shipment shipment = shipmentRepository.findByOrderId(orderId).orElse(null);
        if (shipment == null) {
            Order order = orderRepository.findById(orderId).orElseThrow(() -> ResourceNotFound.of("Order"));
            shipment = Shipment.builder().order(order).build();
        }
        if (courier != null && !courier.isBlank()) shipment.setCourier(courier);
        if (trackingNumber != null && !trackingNumber.isBlank()) shipment.setTrackingNumber(trackingNumber);
        if (shipment.getShippedAt() == null) shipment.setShippedAt(Instant.now());
        shipmentRepository.save(shipment);
    }

    // ---------------------------------------------------------------- Products
    public record AdminProductDto(
            Long id, String name, String slug, String description,
            BigDecimal sellingPrice, BigDecimal mrp,
            Double ratingAvg, Integer ratingCount,
            String primaryImage, Long categoryId, String categoryName,
            Long brandId, String brandName,
            boolean isFeatured, boolean isActive,
            int stockQuantity, int reservedQuantity, int lowStockThreshold) {}

    @Operation(summary = "Paged products for admin management (includes inactive)")
    @GetMapping("/products")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<PagedResult<AdminProductDto>>> listProducts(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(100, size)));
        Page<Product> products = productRepository.searchAdmin(search, pageable);
        List<AdminProductDto> dtos = products.getContent().stream().map(this::toAdminProductDto).toList();
        return ResponseEntity.ok(ApiResponse.ok(new PagedResult<>(dtos, PageResponse.from(products))));
    }

    public record ProductCreateRequest(
            @NotBlank String name,
            String description,
            @NotNull BigDecimal sellingPrice,
            @NotNull BigDecimal mrp,
            @NotNull Long categoryId,
            @NotNull Long brandId,
            Boolean isFeatured,
            String imageUrl,
            Integer initialStock,
            Integer lowStockThreshold) {}

    @Operation(summary = "Create product with inventory and optional first image URL")
    @PostMapping("/products")
    @Transactional
    public ResponseEntity<ApiResponse<AdminProductDto>> createProduct(@Valid @RequestBody ProductCreateRequest req) {
        if (req.sellingPrice().signum() < 0 || req.mrp().signum() < 0) {
            throw BadRequest.of("PRICE_INVALID", "Prices cannot be negative");
        }
        Category cat = categoryRepository.findById(req.categoryId())
                .orElseThrow(() -> ResourceNotFound.of("Category"));
        Brand brand = brandRepository.findById(req.brandId())
                .orElseThrow(() -> ResourceNotFound.of("Brand"));

        String slug = req.name().toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "") + "-" + System.currentTimeMillis() % 10000;

        Product product = Product.builder()
                .name(req.name())
                .slug(slug)
                .description(req.description())
                .sellingPrice(req.sellingPrice())
                .mrp(req.mrp())
                .category(cat)
                .brand(brand)
                .isFeatured(req.isFeatured() != null && req.isFeatured())
                .isActive(true)
                .ratingAvg(BigDecimal.ZERO)
                .ratingCount(0)
                .build();
        product = productRepository.save(product);

        if (req.imageUrl() != null && !req.imageUrl().isBlank()) {
            ProductImage img = ProductImage.builder()
                    .imageUrl(req.imageUrl())
                    .isPrimary(true)
                    .sortOrder(0)
                    .build();
            img.attachTo(product);
            productImageRepository.save(img);
        }

        int stock = req.initialStock() != null ? Math.max(0, req.initialStock()) : 50;
        int threshold = req.lowStockThreshold() != null ? Math.max(1, req.lowStockThreshold()) : 5;
        Inventory inv = Inventory.builder()
                .product(product)
                .totalQuantity(stock)
                .reservedQuantity(0)
                .lowStockThreshold(threshold)
                .build();
        inventoryRepository.save(inv);
        inventoryTransactionRepository.save(InventoryTransaction.builder()
                .inventory(inv)
                .type(InventoryTransactionType.PURCHASE)
                .quantityDelta(stock)
                .note("Initial stock for new product " + product.getSlug())
                .build());

        return ResponseEntity.ok(ApiResponse.ok(toAdminProductDto(product)));
    }

    public record ProductUpdateRequest(
            String name,
            String description,
            BigDecimal sellingPrice,
            BigDecimal mrp,
            Long categoryId,
            Long brandId,
            Boolean isFeatured,
            Boolean isActive) {}

    @Operation(summary = "Update product details")
    @PutMapping("/products/{id}")
    @Transactional
    public ResponseEntity<ApiResponse<AdminProductDto>> updateProduct(
            @PathVariable Long id,
            @RequestBody ProductUpdateRequest req) {
        Product p = productRepository.findById(id).orElseThrow(() -> ResourceNotFound.of("Product"));
        if (req.name() != null && !req.name().isBlank()) p.setName(req.name());
        if (req.description() != null) p.setDescription(req.description());
        if (req.sellingPrice() != null) {
            if (req.sellingPrice().signum() < 0) throw BadRequest.of("PRICE_INVALID", "Selling price cannot be negative");
            p.setSellingPrice(req.sellingPrice());
        }
        if (req.mrp() != null) {
            if (req.mrp().signum() < 0) throw BadRequest.of("PRICE_INVALID", "MRP cannot be negative");
            p.setMrp(req.mrp());
        }
        if (req.isFeatured() != null) p.setIsFeatured(req.isFeatured());
        if (req.isActive() != null) p.setIsActive(req.isActive());
        if (req.categoryId() != null) {
            Category c = categoryRepository.findById(req.categoryId()).orElseThrow(() -> ResourceNotFound.of("Category"));
            p.setCategory(c);
        }
        if (req.brandId() != null) {
            Brand b = brandRepository.findById(req.brandId()).orElseThrow(() -> ResourceNotFound.of("Brand"));
            p.setBrand(b);
        }
        productRepository.save(p);
        return ResponseEntity.ok(ApiResponse.ok(toAdminProductDto(p)));
    }

    @Operation(summary = "Soft delete product")
    @DeleteMapping("/products/{id}")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, String>>> deleteProduct(@PathVariable Long id) {
        Product p = productRepository.findById(id).orElseThrow(() -> ResourceNotFound.of("Product"));
        p.setIsDeleted(true);
        p.setIsActive(false);
        productRepository.save(p);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("message", "Product deleted")));
    }

    public record ActiveToggleRequest(boolean active) {}

    @Operation(summary = "Toggle product active state")
    @PatchMapping("/products/{id}/active")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> toggleProductActive(
            @PathVariable Long id,
            @RequestBody ActiveToggleRequest req) {
        Product p = productRepository.findById(id).orElseThrow(() -> ResourceNotFound.of("Product"));
        p.setIsActive(req.active());
        productRepository.save(p);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("id", id, "isActive", p.getIsActive())));
    }

    @Operation(summary = "Upload a product image (multipart; JPEG/PNG/WEBP ≤5 MB)")
    @PostMapping("/products/{id}/images")
    @Transactional
    public ResponseEntity<ApiResponse<AdminProductDto>> uploadProductImage(
            @PathVariable Long id,
            @RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "false") boolean primary) {
        adminProductService.uploadImage(id, file, primary);
        Product p = productRepository.findById(id).orElseThrow(() -> ResourceNotFound.of("Product"));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(toAdminProductDto(p)));
    }

    private AdminProductDto toAdminProductDto(Product p) {
        Inventory inv = inventoryRepository.findByProductId(p.getId()).orElse(null);
        int total = inv == null ? 0 : inv.getTotalQuantity();
        int reserved = inv == null ? 0 : inv.getReservedQuantity();
        int threshold = inv == null ? 0 : inv.getLowStockThreshold();
        String primaryImg = p.getImages().stream()
                .sorted(java.util.Comparator.comparing(ProductImage::getSortOrder))
                .map(ProductImage::getImageUrl).findFirst().orElse(null);
        return new AdminProductDto(
                p.getId(), p.getName(), p.getSlug(), p.getDescription(),
                p.getSellingPrice(), p.getMrp(),
                p.getRatingAvg() != null ? p.getRatingAvg().doubleValue() : 0.0,
                p.getRatingCount() != null ? p.getRatingCount() : 0,
                primaryImg,
                p.getCategory() != null ? p.getCategory().getId() : null,
                p.getCategory() != null ? p.getCategory().getName() : null,
                p.getBrand() != null ? p.getBrand().getId() : null,
                p.getBrand() != null ? p.getBrand().getName() : null,
                Boolean.TRUE.equals(p.getIsFeatured()),
                Boolean.TRUE.equals(p.getIsActive()),
                total, reserved, threshold);
    }

    // ---------------------------------------------------------------- Categories & Brands
    public record AdminCategoryDto(Long id, String name, String slug, String imageUrl,
                                   Long parentId, boolean isActive) {}

    @Operation(summary = "All categories (including inactive) — DTO projection")
    @GetMapping("/categories")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<List<AdminCategoryDto>>> listAllCategories() {
        return ResponseEntity.ok(ApiResponse.ok(categoryRepository.findAll().stream()
                .map(c -> new AdminCategoryDto(c.getId(), c.getName(), c.getSlug(), c.getImageUrl(),
                        c.getParent() == null ? null : c.getParent().getId(),
                        Boolean.TRUE.equals(c.getIsActive())))
                .toList()));
    }

    public record CategoryRequest(@NotBlank String name, String slug, String imageUrl,
                                  Long parentId, Boolean isActive) {}

    @Operation(summary = "Create a category")
    @PostMapping("/categories")
    @Transactional
    public ResponseEntity<ApiResponse<AdminCategoryDto>> createCategory(@Valid @RequestBody CategoryRequest req) {
        String slug = (req.slug() != null && !req.slug().isBlank())
                ? slugify(req.slug())
                : slugify(req.name());
        if (categoryRepository.existsBySlug(slug)) {
            throw BadRequest.of("SLUG_TAKEN", "A category with this slug already exists");
        }
        Category parent = req.parentId() == null ? null
                : categoryRepository.findById(req.parentId()).orElse(null);
        Category cat = Category.builder()
                .name(req.name())
                .slug(slug)
                .imageUrl(req.imageUrl())
                .parent(parent)
                .isActive(req.isActive() == null || req.isActive())
                .build();
        categoryRepository.save(cat);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(toAdminCategoryDto(cat)));
    }

    @Operation(summary = "Update a category")
    @PutMapping("/categories/{id}")
    @Transactional
    public ResponseEntity<ApiResponse<AdminCategoryDto>> updateCategory(
            @PathVariable Long id, @Valid @RequestBody CategoryRequest req) {
        Category cat = categoryRepository.findById(id).orElseThrow(() -> ResourceNotFound.of("Category"));
        String slug = (req.slug() != null && !req.slug().isBlank())
                ? slugify(req.slug()) : slugify(req.name());
        categoryRepository.findBySlug(slug)
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw BadRequest.of("SLUG_TAKEN", "Another category already uses this slug");
                });
        if (req.parentId() != null) {
            if (req.parentId().equals(id)) {
                throw BadRequest.of("PARENT_SELF", "A category cannot be its own parent");
            }
            cat.setParent(categoryRepository.findById(req.parentId()).orElse(null));
        } else {
            cat.setParent(null);
        }
        cat.setName(req.name());
        cat.setSlug(slug);
        cat.setImageUrl(req.imageUrl());
        if (req.isActive() != null) cat.setIsActive(req.isActive());
        categoryRepository.save(cat);
        return ResponseEntity.ok(ApiResponse.ok(toAdminCategoryDto(cat)));
    }

    @Operation(summary = "Delete a category — blocked while children or products reference it")
    @DeleteMapping("/categories/{id}")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, String>>> deleteCategory(@PathVariable Long id) {
        Category c = categoryRepository.findById(id).orElseThrow(() -> ResourceNotFound.of("Category"));
        if (!categoryRepository.findByParentId(id).isEmpty()) {
            throw BadRequest.of("HAS_CHILDREN", "Delete or move this category's children first");
        }
        if (productRepository.existsByCategoryIdAndIsDeletedFalse(id)) {
            throw BadRequest.of("IN_USE", "Products are still assigned to this category");
        }
        categoryRepository.delete(c);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("message", "Category deleted")));
    }

    public record AdminBrandDto(Long id, String name, String slug, String logoUrl, boolean isActive) {}

    @Operation(summary = "All brands (including inactive) — DTO projection")
    @GetMapping("/brands")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<List<AdminBrandDto>>> listAllBrands() {
        return ResponseEntity.ok(ApiResponse.ok(brandRepository.findAll().stream()
                .map(b -> new AdminBrandDto(b.getId(), b.getName(), b.getSlug(), b.getLogoUrl(),
                        Boolean.TRUE.equals(b.getIsActive())))
                .toList()));
    }

    public record BrandRequest(@NotBlank String name, String slug, String logoUrl, Boolean isActive) {}

    @Operation(summary = "Create a brand")
    @PostMapping("/brands")
    @Transactional
    public ResponseEntity<ApiResponse<AdminBrandDto>> createBrand(@Valid @RequestBody BrandRequest req) {
        String slug = (req.slug() != null && !req.slug().isBlank())
                ? slugify(req.slug()) : slugify(req.name());
        if (brandRepository.existsBySlug(slug)) {
            throw BadRequest.of("SLUG_TAKEN", "A brand with this slug already exists");
        }
        Brand brand = Brand.builder()
                .name(req.name())
                .slug(slug)
                .logoUrl(req.logoUrl())
                .isActive(req.isActive() == null || req.isActive())
                .build();
        brandRepository.save(brand);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(toAdminBrandDto(brand)));
    }

    @Operation(summary = "Update a brand")
    @PutMapping("/brands/{id}")
    @Transactional
    public ResponseEntity<ApiResponse<AdminBrandDto>> updateBrand(
            @PathVariable Long id, @Valid @RequestBody BrandRequest req) {
        Brand brand = brandRepository.findById(id).orElseThrow(() -> ResourceNotFound.of("Brand"));
        String slug = (req.slug() != null && !req.slug().isBlank())
                ? slugify(req.slug()) : slugify(req.name());
        brandRepository.findBySlug(slug)
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw BadRequest.of("SLUG_TAKEN", "Another brand already uses this slug");
                });
        brand.setName(req.name());
        brand.setSlug(slug);
        brand.setLogoUrl(req.logoUrl());
        if (req.isActive() != null) brand.setIsActive(req.isActive());
        brandRepository.save(brand);
        return ResponseEntity.ok(ApiResponse.ok(toAdminBrandDto(brand)));
    }

    @Operation(summary = "Delete a brand — blocked while products reference it")
    @DeleteMapping("/brands/{id}")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, String>>> deleteBrand(@PathVariable Long id) {
        Brand brand = brandRepository.findById(id).orElseThrow(() -> ResourceNotFound.of("Brand"));
        if (productRepository.existsByBrandIdAndIsDeletedFalse(id)) {
            throw BadRequest.of("IN_USE", "Products are still assigned to this brand");
        }
        brandRepository.delete(brand);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("message", "Brand deleted")));
    }

    // ---------------------------------------------------------------- Customers
    public record CustomerDto(
            Long id, String email, String fullName, String phone,
            boolean isActive, boolean isEmailVerified, Instant createdAt) {}

    @Operation(summary = "Search customers (never exposes credentials)")
    @GetMapping("/customers")
    public ResponseEntity<ApiResponse<PagedResult<CustomerDto>>> listCustomers(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(100, size)));
        Page<User> users = userRepository.searchAdmin(q, active, pageable);
        List<CustomerDto> dtos = users.getContent().stream().map(u -> new CustomerDto(
                u.getId(), u.getEmail(), u.getFullName(), u.getPhone(),
                Boolean.TRUE.equals(u.getIsActive()),
                Boolean.TRUE.equals(u.getIsEmailVerified()),
                u.getCreatedAt())).toList();
        return ResponseEntity.ok(ApiResponse.ok(new PagedResult<>(dtos, PageResponse.from(users))));
    }

    public record CustomerActiveRequest(boolean active) {}

    @Operation(summary = "Activate / deactivate a customer account")
    @PutMapping("/customers/{id}/active")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> setCustomerActive(
            @PathVariable Long id,
            @RequestBody CustomerActiveRequest req) {
        User u = userRepository.findById(id)
                .filter(user -> Boolean.FALSE.equals(user.getIsDeleted()))
                .orElseThrow(() -> ResourceNotFound.of("Customer"));
        u.setIsActive(req.active());
        userRepository.save(u);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("id", id, "isActive", u.getIsActive())));
    }

    // ---------------------------------------------------------------- Inventory
    public record InventoryAdminDto(
            Long inventoryId, Long productId, String productName,
            int totalQuantity, int reservedQuantity, int availableQuantity,
            int lowStockThreshold, boolean isLowStock) {}

    @Operation(summary = "Inventory rows (lowOnly=true returns only low-stock products, worst first)")
    @GetMapping("/inventory")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<List<InventoryAdminDto>>> listInventory(
            @RequestParam(defaultValue = "false") boolean lowOnly) {
        List<Inventory> list = lowOnly ? inventoryRepository.findLowStock() : inventoryRepository.findAll();
        List<InventoryAdminDto> dtos = list.stream()
                .sorted(java.util.Comparator.comparingInt(Inventory::availableQuantity))
                .map(this::toInventoryDto).toList();
        return ResponseEntity.ok(ApiResponse.ok(dtos));
    }

    public record StockAdjustmentRequest(@NotNull Integer delta, @NotBlank String reason) {}

    @Operation(summary = "Adjust stock by delta (audited; guards reserved units; alerts on low stock)")
    @PutMapping("/inventory/{productId}")
    @Transactional
    public ResponseEntity<ApiResponse<InventoryAdminDto>> adjustStock(
            @PathVariable Long productId,
            @Valid @RequestBody StockAdjustmentRequest req) {
        AdminInventoryService.AdjustResponse result =
                adminInventoryService.adjust(productId, new AdminInventoryService.AdjustRequest(
                        req.delta(), req.reason()));
        Inventory inv = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> ResourceNotFound.of("Inventory for product " + productId));
        return ResponseEntity.ok(ApiResponse.ok(toInventoryDto(inv)));
    }

    private InventoryAdminDto toInventoryDto(Inventory inv) {
        return new InventoryAdminDto(
                inv.getId(), inv.getProduct().getId(), inv.getProduct().getName(),
                inv.getTotalQuantity(), inv.getReservedQuantity(), inv.availableQuantity(),
                inv.getLowStockThreshold(), inv.isLowStock());
    }

    // ---------------------------------------------------------------- Coupons
    public record CouponAdminDto(Long id, String code, String discountType, BigDecimal discountValue,
                                 BigDecimal minOrderAmount, BigDecimal maxDiscountAmount,
                                 Instant expiresAt, Integer maxGlobalUses, Integer maxUsesPerUser,
                                 Boolean isActive, Instant createdAt) {}

    @Operation(summary = "All non-deleted coupons — DTO projection")
    @GetMapping("/coupons")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<List<CouponAdminDto>>> listCoupons() {
        return ResponseEntity.ok(ApiResponse.ok(couponRepository
                .findAllByDeletedAtIsNullOrderByCreatedAtDesc().stream()
                .map(this::toCouponDto).toList()));
    }

    public record CouponRequest(
            @NotBlank String code,
            @NotNull DiscountType discountType,
            @NotNull BigDecimal discountValue,
            BigDecimal minOrderAmount,
            BigDecimal maxDiscountAmount,
            Instant expiresAt,
            Integer maxGlobalUses,
            Integer maxUsesPerUser,
            Boolean isActive) {}

    @Operation(summary = "Create a coupon")
    @PostMapping("/coupons")
    @Transactional
    public ResponseEntity<ApiResponse<CouponAdminDto>> createCoupon(@Valid @RequestBody CouponRequest req) {
        validateCoupon(req);
        String code = req.code().toUpperCase().trim();
        if (couponRepository.existsByCodeIgnoreCase(code)) {
            throw BusinessException.conflict("COUPON_EXISTS", "A coupon with this code already exists");
        }
        Coupon c = Coupon.builder()
                .code(code)
                .discountType(req.discountType())
                .discountValue(req.discountValue())
                .minOrderAmount(req.minOrderAmount() != null ? req.minOrderAmount() : BigDecimal.ZERO)
                .maxDiscountAmount(req.maxDiscountAmount())
                .expiresAt(req.expiresAt())
                .maxGlobalUses(req.maxGlobalUses())
                .maxUsesPerUser(req.maxUsesPerUser() != null ? req.maxUsesPerUser() : 1)
                .isActive(req.isActive() == null || req.isActive())
                .build();
        couponRepository.save(c);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(toCouponDto(c)));
    }

    @Operation(summary = "Update a coupon")
    @PutMapping("/coupons/{id}")
    @Transactional
    public ResponseEntity<ApiResponse<CouponAdminDto>> updateCoupon(
            @PathVariable Long id, @Valid @RequestBody CouponRequest req) {
        validateCoupon(req);
        Coupon c = couponRepository.findById(id)
                .filter(coupon -> coupon.getDeletedAt() == null)
                .orElseThrow(() -> ResourceNotFound.of("Coupon"));
        String code = req.code().toUpperCase().trim();
        couponRepository.findByCodeIgnoreCaseAndIsActiveTrue(code)
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw BusinessException.conflict("COUPON_EXISTS", "Another coupon already uses this code");
                });
        c.setCode(code);
        c.setDiscountType(req.discountType());
        c.setDiscountValue(req.discountValue());
        c.setMinOrderAmount(req.minOrderAmount() != null ? req.minOrderAmount() : BigDecimal.ZERO);
        c.setMaxDiscountAmount(req.maxDiscountAmount());
        c.setExpiresAt(req.expiresAt());
        c.setMaxGlobalUses(req.maxGlobalUses());
        c.setMaxUsesPerUser(req.maxUsesPerUser() != null ? req.maxUsesPerUser() : 1);
        if (req.isActive() != null) c.setIsActive(req.isActive());
        couponRepository.save(c);
        return ResponseEntity.ok(ApiResponse.ok(toCouponDto(c)));
    }

    @Operation(summary = "Soft-delete a coupon (redemption history keeps its FK)")
    @DeleteMapping("/coupons/{id}")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, String>>> deleteCoupon(@PathVariable Long id) {
        Coupon c = couponRepository.findById(id)
                .filter(coupon -> coupon.getDeletedAt() == null)
                .orElseThrow(() -> ResourceNotFound.of("Coupon"));
        c.setDeletedAt(Instant.now());
        c.setIsActive(false);
        couponRepository.save(c);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("message", "Coupon deleted")));
    }

    private void validateCoupon(CouponRequest req) {
        if (req.discountType() == DiscountType.PERCENT
                && req.discountValue().compareTo(BigDecimal.valueOf(100)) > 0) {
            throw BadRequest.of("DISCOUNT_INVALID", "Percentage discount cannot exceed 100");
        }
        if (req.maxUsesPerUser() != null && req.maxUsesPerUser() < 1) {
            throw BadRequest.of("LIMIT_INVALID", "Per-user limit must be at least 1");
        }
        if (req.maxGlobalUses() != null && req.maxGlobalUses() < 1) {
            throw BadRequest.of("LIMIT_INVALID", "Global limit must be at least 1");
        }
        if (req.expiresAt() != null && req.expiresAt().isBefore(Instant.now())) {
            throw BadRequest.of("EXPIRY_INVALID", "Expiry must be in the future");
        }
    }

    private CouponAdminDto toCouponDto(Coupon coupon) {
        return new CouponAdminDto(coupon.getId(), coupon.getCode(),
                coupon.getDiscountType().name(), coupon.getDiscountValue(),
                coupon.getMinOrderAmount(), coupon.getMaxDiscountAmount(),
                coupon.getExpiresAt(), coupon.getMaxGlobalUses(), coupon.getMaxUsesPerUser(),
                coupon.getIsActive(), coupon.getCreatedAt());
    }

    // ---------------------------------------------------------------- Returns & refunds
    public record ReturnDto(
            Long id, Long orderId, String orderNumber,
            String customerEmail, String reason, String status, Instant createdAt) {}

    @Operation(summary = "Return requests (optionally filtered by status)")
    @GetMapping("/returns")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<PagedResult<ReturnDto>>> listReturns(
            @RequestParam(required = false) ReturnStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(100, size)));
        Page<ReturnRequest> requests = (status != null)
                ? returnRequestRepository.findByStatusOrderByCreatedAtDesc(status, pageable)
                : returnRequestRepository.findAllByOrderByCreatedAtDesc(pageable);

        List<ReturnDto> dtos = requests.getContent().stream().map(r -> new ReturnDto(
                r.getId(),
                r.getOrder().getId(),
                r.getOrder().getOrderNumber(),
                r.getUser().getEmail(),
                r.getReason(),
                r.getStatus().name(),
                r.getCreatedAt())).toList();
        return ResponseEntity.ok(ApiResponse.ok(new PagedResult<>(dtos, PageResponse.from(requests))));
    }

    public record UpdateReturnStatusRequest(@NotNull ReturnStatus status, String note) {}

    /**
     * Full lifecycle: REJECTED → order back to DELIVERED; RETURNED → units
     * restored, order RETURNED, payment marked REFUNDED with a Refund ledger
     * row (gateway refund itself is out of band for test mode).
     */
    @Operation(summary = "Decide a return (APPROVED/REJECTED/PICKED/RETURNED) with refunds and restock")
    @PutMapping("/returns/{id}/status")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, String>>> updateReturnStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateReturnStatusRequest req) {
        ReturnRequest rr = returnRequestRepository.findById(id)
                .orElseThrow(() -> ResourceNotFound.of("Return request"));
        ReturnStatus current = rr.getStatus();
        if (current == ReturnStatus.REJECTED || current == ReturnStatus.RETURNED) {
            throw BadRequest.of("RETURN_CLOSED", "This return request is already closed");
        }
        if (current != ReturnStatus.REQUESTED
                && (req.status() == ReturnStatus.APPROVED || req.status() == ReturnStatus.REJECTED)) {
            throw BadRequest.of("RETURN_STATE_INVALID", "Return already past the decision stage");
        }

        rr.setStatus(req.status());
        returnRequestRepository.save(rr);

        Order order = rr.getOrder();
        switch (req.status()) {
            case REJECTED -> {
                if (order.getStatus() == OrderStatus.RETURN_REQUESTED) {
                    OrderStatus from = order.getStatus();
                    order.setStatus(OrderStatus.DELIVERED);
                    orderRepository.save(order);
                    appendHistory(order, from, "admin",
                            req.note() == null || req.note().isBlank()
                                    ? "Return rejected" : "Return rejected: " + req.note());
                }
            }
            case RETURNED -> {
                OrderStatus from = order.getStatus();
                order.setStatus(OrderStatus.RETURNED);
                orderRepository.save(order);
                appendHistory(order, from, "admin",
                        req.note() == null || req.note().isBlank()
                                ? "Return completed — refund issued"
                                : "Return completed — " + req.note());

                // Restock the returned units and write RETURN audit rows.
                for (OrderItem oi : order.getItems()) {
                    inventoryRepository.restock(oi.getProduct().getId(), oi.getQuantity());
                    inventoryRepository.findByProductId(oi.getProduct().getId()).ifPresent(inv ->
                            inventoryTransactionRepository.save(InventoryTransaction.builder()
                                    .inventory(inv)
                                    .type(InventoryTransactionType.RETURN)
                                    .quantityDelta(oi.getQuantity())
                                    .note("Units restored — return completed for order "
                                            + order.getOrderNumber())
                                    .orderId(order.getId())
                                    .build()));
                }

                // Refund ledger: mark the payment REFUNDED (test-mode money movement).
                paymentRepository.findByOrderId(order.getId()).ifPresent(payment -> {
                    if (payment.getStatus() == Payment.PaymentStatus.PAID) {
                        payment.setStatus(Payment.PaymentStatus.REFUNDED);
                        paymentRepository.save(payment);
                        refundRepository.save(Refund.builder()
                                .returnRequest(rr)
                                .payment(payment)
                                .refundAmount(order.getTotalAmount())
                                .status(Refund.RefundStatus.COMPLETED)
                                .build());
                    }
                });
            }
            default -> { /* APPROVED / PICKED — status row only */ }
        }

        notificationSaver.orderCreated(order.getUser().getId(),
                "Return " + req.status() + " — order " + order.getOrderNumber(),
                req.note() == null || req.note().isBlank()
                        ? "Your return request is now " + req.status() + "." : req.note());

        return ResponseEntity.ok(ApiResponse.ok(Map.of("message",
                "Return request updated to " + req.status())));
    }

    // ---------------------------------------------------------------- Review moderation
    public record AdminReviewDto(Long id, Long productId, String productName, String reviewerEmail,
                                 Integer rating, String title, String body, Boolean verified,
                                 Instant createdAt) {}

    @Operation(summary = "Paged review feed (newest first) for moderation")
    @GetMapping("/reviews")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<PagedResult<AdminReviewDto>>> listReviews(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<Review> reviews = reviewRepository.findAll(PageRequest.of(
                Math.max(0, page), Math.max(1, Math.min(100, size)),
                org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Direction.DESC, "createdAt")));
        List<AdminReviewDto> dtos = reviews.getContent().stream().map(r -> new AdminReviewDto(
                r.getId(), r.getProduct().getId(), r.getProduct().getName(),
                r.getUser().getEmail(), r.getRating(), r.getTitle(), r.getBody(),
                Boolean.TRUE.equals(r.getIsVerified()), r.getCreatedAt())).toList();
        return ResponseEntity.ok(ApiResponse.ok(new PagedResult<>(dtos, PageResponse.from(reviews))));
    }

    @Operation(summary = "Delete an abusive review (moderation action)")
    @DeleteMapping("/reviews/{id}")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, String>>> deleteReview(@PathVariable Long id) {
        Review review = reviewRepository.findById(id).orElseThrow(() -> ResourceNotFound.of("Review"));
        reviewRepository.delete(review);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("message", "Review removed by moderation")));
    }

    // ---------------------------------------------------------------- internals
    private AdminCategoryDto toAdminCategoryDto(Category c) {
        return new AdminCategoryDto(c.getId(), c.getName(), c.getSlug(), c.getImageUrl(),
                c.getParent() == null ? null : c.getParent().getId(),
                Boolean.TRUE.equals(c.getIsActive()));
    }

    private AdminBrandDto toAdminBrandDto(Brand b) {
        return new AdminBrandDto(b.getId(), b.getName(), b.getSlug(), b.getLogoUrl(),
                Boolean.TRUE.equals(b.getIsActive()));
    }

    private void appendHistory(Order order, OrderStatus from, String actor, String note) {
        OrderStatusHistory history = OrderStatusHistory.builder()
                .previousStatus(from).newStatus(order.getStatus())
                .changedBy(actor).note(note)
                .build();
        history.attachTo(order);
        statusHistoryRepository.save(history);
    }

    private String slugify(String raw) {
        return raw.trim().toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
    }

    public record PagedResult<T>(List<T> items, PageResponse meta) {}
}
