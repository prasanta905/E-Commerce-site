package com.shopsphere.admin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.shopsphere.brand.entity.Brand;
import com.shopsphere.brand.repository.BrandRepository;
import com.shopsphere.category.entity.Category;
import com.shopsphere.category.repository.CategoryRepository;
import com.shopsphere.common.PageResponse;
import com.shopsphere.common.exception.BadRequest;
import com.shopsphere.common.exception.ResourceNotFound;
import com.shopsphere.inventory.entity.Inventory;
import com.shopsphere.inventory.entity.InventoryTransactionType;
import com.shopsphere.inventory.repository.InventoryRepository;
import com.shopsphere.inventory.repository.InventoryTransactionRepository;
import com.shopsphere.product.entity.Product;
import com.shopsphere.product.entity.ProductImage;
import com.shopsphere.product.repository.ProductImageRepository;
import com.shopsphere.product.repository.ProductRepository;

/**
 * Admin catalog management: product CRUD (soft delete), image uploads into the
 * local /uploads tree, and category/brand CRUD with dependency-safe deletes.
 */
@Service
public class AdminProductService {

    private static final Set<String> ALLOWED_IMAGE_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp");
    private static final long MAX_IMAGE_BYTES = 5 * 1024 * 1024; // 5 MB

    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;
    private final Path uploadRoot;

    public AdminProductService(ProductRepository productRepository,
                               ProductImageRepository productImageRepository,
                               CategoryRepository categoryRepository,
                               BrandRepository brandRepository,
                               InventoryRepository inventoryRepository,
                               InventoryTransactionRepository inventoryTransactionRepository,
                               @Value("${shopsphere.app.upload-dir:uploads}") String uploadDir) {
        this.productRepository = productRepository;
        this.productImageRepository = productImageRepository;
        this.categoryRepository = categoryRepository;
        this.brandRepository = brandRepository;
        this.inventoryRepository = inventoryRepository;
        this.inventoryTransactionRepository = inventoryTransactionRepository;
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    // ------------------------------------------------------------ products
    @Transactional(readOnly = true)
    public Page<AdminProductDto> listProducts(String search, int page, int size) {
        Page<Product> products = productRepository.searchAdmin(blankToNull(search),
                PageRequest.of(Math.max(0, page), Math.max(1, Math.min(100, size))));
        return products.map(this::toAdminDto);
    }

    @Transactional
    public AdminProductDto createProduct(AdminProductUpsert request) {
        String slug = normalizeSlug(request.slug(), request.name());
        if (productRepository.findBySlugAndIsDeletedFalse(slug).isPresent()) {
            throw BadRequest.of("SLUG_TAKEN", "A product with this slug already exists");
        }
        Category category = requireCategory(request.categoryId());
        Brand brand = requireBrand(request.brandId());

        Product product = Product.builder()
                .category(category)
                .brand(brand)
                .name(request.name().trim())
                .slug(slug)
                .description(request.description())
                .mrp(request.mrp())
                .sellingPrice(request.sellingPrice())
                .isFeatured(Boolean.TRUE.equals(request.isFeatured()))
                .isActive(request.isActive() == null || request.isActive())
                .build();
        productRepository.save(product);

        if (request.stock() != null) {
            if (request.stock() < 0) {
                throw BadRequest.of("STOCK_INVALID", "Stock cannot be negative");
            }
            Inventory inventory = Inventory.builder()
                    .product(product)
                    .totalQuantity(request.stock())
                    .lowStockThreshold(request.lowStockThreshold() == null ? 10 : request.lowStockThreshold())
                    .build();
            product.setInventory(inventory);
            inventoryRepository.save(inventory);
            inventoryTransactionRepository.save(com.shopsphere.inventory.entity.InventoryTransaction.builder()
                    .inventory(inventory)
                    .type(InventoryTransactionType.PURCHASE)
                    .quantityDelta(request.stock())
                    .note("Initial stock for new product " + product.getSlug())
                    .build());
        }
        return toAdminDto(product);
    }

    @Transactional
    public AdminProductDto updateProduct(Long id, AdminProductUpsert request) {
        Product product = requireProduct(id);
        String slug = normalizeSlug(request.slug(), request.name());
        productRepository.findBySlugAndIsDeletedFalse(slug)
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw BadRequest.of("SLUG_TAKEN", "Another product already uses this slug");
                });

        product.setCategory(requireCategory(request.categoryId()));
        product.setBrand(requireBrand(request.brandId()));
        product.setName(request.name().trim());
        product.setSlug(slug);
        product.setDescription(request.description());
        product.setMrp(request.mrp());
        product.setSellingPrice(request.sellingPrice());
        product.setIsFeatured(Boolean.TRUE.equals(request.isFeatured()));
        if (request.isActive() != null) {
            product.setIsActive(request.isActive());
        }
        productRepository.save(product);

        if (request.stock() != null) {
            adjustInventoryRow(product, request.stock(), request.lowStockThreshold(), "Product edit");
        }
        return toAdminDto(product);
    }

    /** Soft delete — catalogues keep referential history (orders, reviews). */
    @Transactional
    public void deleteProduct(Long id) {
        Product product = requireProduct(id);
        product.setIsDeleted(true);
        product.setIsActive(false);
        productRepository.save(product);
    }

    @Transactional
    public AdminProductDto setActive(Long id, boolean active) {
        Product product = requireProduct(id);
        product.setIsActive(active);
        productRepository.save(product);
        return toAdminDto(product);
    }

    // -------------------------------------------------------------- images
    @Transactional
    public AdminProductDto uploadImage(Long productId, MultipartFile file, boolean primary) {
        Product product = requireProduct(productId);
        if (file == null || file.isEmpty()) {
            throw BadRequest.of("FILE_EMPTY", "No image file supplied");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw BadRequest.of("FILE_TOO_LARGE", "Image must be 5 MB or smaller");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!ALLOWED_IMAGE_TYPES.contains(contentType)) {
            throw BadRequest.of("FILE_TYPE_INVALID", "Only JPEG, PNG or WEBP images are allowed");
        }

        try {
            Path productDir = uploadRoot.resolve("products");
            Files.createDirectories(productDir);
            String original = file.getOriginalFilename() == null ? "image" : file.getOriginalFilename();
            String ext = original.lastIndexOf('.') >= 0
                    ? original.substring(original.lastIndexOf('.')).toLowerCase(Locale.ROOT) : ".jpg";
            String safeName = "p" + product.getId() + "-"
                    + Long.toString(System.currentTimeMillis(), 36) + ext;

            Path target = productDir.resolve(safeName).normalize();
            if (!target.startsWith(productDir)) {
                throw BadRequest.of("FILE_PATH_INVALID", "Unsafe upload path");
            }
            try (var in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }

            if (primary) {
                product.getImages().forEach(img -> img.setIsPrimary(false));
            }
            boolean isFirst = product.getImages().isEmpty();
            ProductImage image = ProductImage.builder()
                    .imageUrl("/uploads/products/" + safeName)
                    .isPrimary(primary || isFirst)
                    .sortOrder(product.getImages().size())
                    .build();
            image.attachTo(product);
            product.getImages().add(image);
            productImageRepository.save(image);
            return toAdminDto(product);
        } catch (IOException e) {
            throw new IllegalStateException("Image upload failed", e);
        }
    }

    // ---------------------------------------------------------- categories
    @Transactional
    public CategoryServiceBridge createCategory(AdminCategoryUpsert request) {
        String slug = slugify(request.slug(), request.name());
        if (categoryRepository.existsBySlug(slug)) {
            throw BadRequest.of("SLUG_TAKEN", "A category with this slug already exists");
        }
        Category parent = request.parentId() == null ? null
                : categoryRepository.findById(request.parentId())
                        .orElseThrow(() -> ResourceNotFound.of("Parent category"));
        Category category = Category.builder()
                .parent(parent)
                .name(request.name().trim())
                .slug(slug)
                .imageUrl(request.imageUrl())
                .isActive(request.isActive() == null || request.isActive())
                .build();
        categoryRepository.save(category);
        return toCategoryDto(category);
    }

    @Transactional
    public CategoryServiceBridge updateCategory(Long id, AdminCategoryUpsert request) {
        Category category = categoryRepository.findById(id).orElseThrow(() -> ResourceNotFound.of("Category"));
        String slug = slugify(request.slug(), request.name());
        categoryRepository.findBySlug(slug)
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw BadRequest.of("SLUG_TAKEN", "Another category already uses this slug");
                });
        if (request.parentId() != null) {
            if (request.parentId().equals(id)) {
                throw BadRequest.of("PARENT_SELF", "A category cannot be its own parent");
            }
            category.setParent(categoryRepository.findById(request.parentId())
                    .orElseThrow(() -> ResourceNotFound.of("Parent category")));
        } else {
            category.setParent(null);
        }
        category.setName(request.name().trim());
        category.setSlug(slug);
        category.setImageUrl(request.imageUrl());
        if (request.isActive() != null) {
            category.setIsActive(request.isActive());
        }
        categoryRepository.save(category);
        return toCategoryDto(category);
    }

    @Transactional
    public void deleteCategory(Long id) {
        Category category = categoryRepository.findById(id).orElseThrow(() -> ResourceNotFound.of("Category"));
        if (categoryRepository.countByParentIdAndIsActiveTrue(id) > 0
                || !categoryRepository.findByParentId(id).isEmpty()) {
            throw BadRequest.of("HAS_CHILDREN", "Delete or move this category's children first");
        }
        if (productRepository.existsByCategoryIdAndIsDeletedFalse(id)) {
            throw BadRequest.of("IN_USE", "Products are still assigned to this category");
        }
        categoryRepository.delete(category);
    }

    // -------------------------------------------------------------- brands
    @Transactional
    public BrandSummary createBrand(AdminBrandUpsert request) {
        String slug = slugify(request.slug(), request.name());
        if (brandRepository.existsBySlug(slug)) {
            throw BadRequest.of("SLUG_TAKEN", "A brand with this slug already exists");
        }
        Brand brand = Brand.builder()
                .name(request.name().trim())
                .slug(slug)
                .logoUrl(request.logoUrl())
                .isActive(request.isActive() == null || request.isActive())
                .build();
        brandRepository.save(brand);
        return toBrandDto(brand);
    }

    @Transactional
    public BrandSummary updateBrand(Long id, AdminBrandUpsert request) {
        Brand brand = brandRepository.findById(id).orElseThrow(() -> ResourceNotFound.of("Brand"));
        String slug = slugify(request.slug(), request.name());
        brandRepository.findBySlug(slug)
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw BadRequest.of("SLUG_TAKEN", "Another brand already uses this slug");
                });
        brand.setName(request.name().trim());
        brand.setSlug(slug);
        brand.setLogoUrl(request.logoUrl());
        if (request.isActive() != null) {
            brand.setIsActive(request.isActive());
        }
        brandRepository.save(brand);
        return toBrandDto(brand);
    }

    @Transactional
    public void deleteBrand(Long id) {
        Brand brand = brandRepository.findById(id).orElseThrow(() -> ResourceNotFound.of("Brand"));
        if (productRepository.existsByBrandIdAndIsDeletedFalse(id)) {
            throw BadRequest.of("IN_USE", "Products are still assigned to this brand");
        }
        brandRepository.delete(brand);
    }

    // ------------------------------------------------------------ internals
    private void adjustInventoryRow(Product product, Integer stock, Integer threshold, String reason) {
        Inventory inventory = inventoryRepository.findByProductId(product.getId())
                .orElseGet(() -> {
                    Inventory created = Inventory.builder().product(product).totalQuantity(0).build();
                    product.setInventory(created);
                    return inventoryRepository.save(created);
                });
        int delta = stock - inventory.getTotalQuantity();
        if (delta < 0 && inventory.availableQuantity() < -delta) {
            throw BadRequest.of("STOCK_RESERVED", "Cannot reduce stock below the reserved quantity");
        }
        inventory.setTotalQuantity(stock);
        if (threshold != null) {
            inventory.setLowStockThreshold(threshold);
        }
        inventoryRepository.save(inventory);
        inventoryTransactionRepository.save(com.shopsphere.inventory.entity.InventoryTransaction.builder()
                .inventory(inventory)
                .type(InventoryTransactionType.ADJUST)
                .quantityDelta(delta)
                .note(reason + " — total set to " + stock)
                .build());
    }

    private Product requireProduct(Long id) {
        return productRepository.findById(id).orElseThrow(() -> ResourceNotFound.of("Product"));
    }

    private Category requireCategory(Long id) {
        if (id == null) throw BadRequest.of("CATEGORY_REQUIRED", "Category is required");
        Category category = categoryRepository.findById(id).orElseThrow(() -> ResourceNotFound.of("Category"));
        if (!Boolean.TRUE.equals(category.getIsActive())) {
            throw BadRequest.of("CATEGORY_INACTIVE", "Selected category is inactive");
        }
        return category;
    }

    private Brand requireBrand(Long id) {
        if (id == null) throw BadRequest.of("BRAND_REQUIRED", "Brand is required");
        Brand brand = brandRepository.findById(id).orElseThrow(() -> ResourceNotFound.of("Brand"));
        if (!Boolean.TRUE.equals(brand.getIsActive())) {
            throw BadRequest.of("BRAND_INACTIVE", "Selected brand is inactive");
        }
        return brand;
    }

    private String normalizeSlug(String provided, String name) {
        String base = provided == null || provided.isBlank() ? name : provided;
        String slug = slugify(base, name);
        if (slug.isBlank()) {
            throw BadRequest.of("SLUG_INVALID", "Product name must contain letters or numbers");
        }
        return slug;
    }

    private String slugify(String provided, String fallback) {
        String source = provided == null || provided.isBlank() ? fallback : provided;
        return source.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
    }

    private String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    AdminProductDto toAdminDto(Product product) {
        List<String> imageUrls = product.getImages().stream()
                .sorted(java.util.Comparator.comparing(ProductImage::getSortOrder))
                .map(ProductImage::getImageUrl).toList();
        return new AdminProductDto(
                product.getId(), product.getName(), product.getSlug(), product.getDescription(),
                product.getCategory().getId(), product.getCategory().getName(),
                product.getBrand() == null ? null : product.getBrand().getId(),
                product.getBrand() == null ? null : product.getBrand().getName(),
                product.getMrp(), product.getSellingPrice(),
                product.getRatingAvg(), product.getRatingCount(),
                product.getInventory() == null ? 0 : product.getInventory().getTotalQuantity(),
                product.getInventory() == null ? 0 : product.getInventory().availableQuantity(),
                product.getIsFeatured(), product.getIsActive(), product.getIsDeleted(),
                imageUrls);
    }

    private CategoryServiceBridge toCategoryDto(Category category) {
        return new CategoryServiceBridge(category.getId(), category.getName(), category.getSlug(),
                category.getImageUrl(), category.getParent() == null ? null : category.getParent().getId(),
                Boolean.TRUE.equals(category.getIsActive()));
    }

    private BrandSummary toBrandDto(Brand brand) {
        return new BrandSummary(brand.getId(), brand.getName(), brand.getSlug(),
                brand.getLogoUrl(), Boolean.TRUE.equals(brand.getIsActive()));
    }

    /** Admin-facing DTOs (records kept local to the admin module). */
    public record AdminProductDto(Long id, String name, String slug, String description,
                                  Long categoryId, String categoryName,
                                  Long brandId, String brandName,
                                  java.math.BigDecimal mrp, java.math.BigDecimal sellingPrice,
                                  java.math.BigDecimal ratingAvg, Integer ratingCount,
                                  int stock, int available,
                                  Boolean isFeatured, Boolean isActive, Boolean isDeleted,
                                  List<String> imageUrls) {}

    public record AdminProductUpsert(String name, String slug, String description,
                                     Long categoryId, Long brandId,
                                     java.math.BigDecimal mrp, java.math.BigDecimal sellingPrice,
                                     Integer stock, Integer lowStockThreshold,
                                     Boolean isFeatured, Boolean isActive) {}

    public record AdminCategoryUpsert(String name, String slug, String imageUrl,
                                      Long parentId, Boolean isActive) {}

    public record AdminBrandUpsert(String name, String slug, String logoUrl, Boolean isActive) {}

    /** Carries isActive — the public projection hides it. */
    public record CategoryServiceBridge(Long id, String name, String slug, String imageUrl,
                                        Long parentId, boolean isActive) {}

    public record BrandSummary(Long id, String name, String slug, String logoUrl, boolean isActive) {}
}
