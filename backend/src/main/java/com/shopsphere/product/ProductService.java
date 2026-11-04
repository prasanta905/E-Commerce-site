package com.shopsphere.product;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopsphere.common.exception.BusinessException;
import com.shopsphere.common.exception.ResourceNotFound;
import com.shopsphere.inventory.repository.InventoryRepository;
import com.shopsphere.product.entity.Product;
import com.shopsphere.product.repository.ProductImageRepository;
import com.shopsphere.product.repository.ProductRepository;
import com.shopsphere.product.repository.ProductSpecificationRepository;

/**
 * Public catalog service: filtering, sorting, pagination, suggestions.
 * All money totals etc. are server-computed; DTOs never leak entities.
 */
@Service
public class ProductService {

    private static final int MAX_PAGE_SIZE = 60;

    private final ProductRepository productRepository;
    private final ProductImageRepository imageRepository;
    private final ProductSpecificationRepository specificationRepository;
    private final InventoryRepository inventoryRepository;
    private final ProductMapper mapper;

    public ProductService(ProductRepository productRepository,
                          ProductImageRepository imageRepository,
                          ProductSpecificationRepository specificationRepository,
                          InventoryRepository inventoryRepository,
                          ProductMapper mapper) {
        this.productRepository = productRepository;
        this.imageRepository = imageRepository;
        this.specificationRepository = specificationRepository;
        this.inventoryRepository = inventoryRepository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public ProductListResponse search(Long categoryId, Long brandId, String search,
                                      BigDecimal minPrice, BigDecimal maxPrice,
                                      Double minRating, Boolean inStock,
                                      String sort, int page, int size) {
        String term = (search == null || search.isBlank()) ? null : search.trim();
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(MAX_PAGE_SIZE, Math.max(1, size)),
                buildSort(sort));
        Page<Product> result = productRepository.searchCatalogue(
                term, minPrice, maxPrice, categoryId, brandId, minRating, inStock, pageable);
        return mapper.toListResponse(result);
    }

    @Transactional(readOnly = true)
    public ProductDetailDto getDetail(Long id) {
        Product product = productRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> ResourceNotFound.of("Product"));

        var images = imageRepository.findByProductIdOrderBySortOrderAsc(id);
        var specs = specificationRepository.findByProductId(id);
        var inventory = inventoryRepository.findByProductId(id).orElse(null);
        List<Product> related = productRepository.findRelated(
                product.getCategory().getId(), id, PageRequest.of(0, 6)).getContent();
        return mapper.toDetail(product, images, specs, inventory, related);
    }

    @Transactional(readOnly = true)
    public ProductListResponse featured(int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(MAX_PAGE_SIZE, Math.max(1, size)),
                Sort.by(Sort.Direction.DESC, "ratingAvg"));
        return mapper.toListResponse(productRepository.findByIsFeaturedTrueAndIsActiveTrueAndIsDeletedFalse(pageable));
    }

    @Transactional(readOnly = true)
    public ProductListResponse newArrivals(int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(MAX_PAGE_SIZE, Math.max(1, size)),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return mapper.toListResponse(productRepository.findByIsActiveTrueAndIsDeletedFalseOrderByCreatedAtDesc(pageable));
    }

    @Transactional(readOnly = true)
    public ProductListResponse bestSellers(int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(MAX_PAGE_SIZE, Math.max(1, size)),
                Sort.by(Sort.Direction.DESC, "ratingCount"));
        return mapper.toListResponse(productRepository.findByIsActiveTrueAndIsDeletedFalseOrderByRatingCountDesc(pageable));
    }

    @Transactional(readOnly = true)
    public List<ProductCardDto> searchSuggestions(String q) {
        if (q == null || q.isBlank()) return List.of();
        return productRepository
                .findByNameContainingIgnoreCaseAndIsActiveTrueAndIsDeletedFalse(q.trim(), PageRequest.of(0, 8))
                .getContent().stream()
                .map(p -> mapper.toCard(p, inventoryRepository.findByProductId(p.getId()).orElse(null)))
                .toList();
    }

    @Transactional(readOnly = true)
    public ProductListResponse byCategory(Long categoryId, String sort, int page, int size) {
        return search(categoryId, null, null, null, null, null, null, sort, page, size);
    }

    @Transactional(readOnly = true)
    public ProductListResponse byBrand(Long brandId, String sort, int page, int size) {
        return search(null, brandId, null, null, null, null, null, sort, page, size);
    }

    private Sort buildSort(String sort) {
        return switch (sort == null ? "" : sort.toLowerCase(Locale.ROOT)) {
            case "price" -> Sort.by(Sort.Direction.ASC, "sellingPrice");
            case "price_desc" -> Sort.by(Sort.Direction.DESC, "sellingPrice");
            case "rating" -> Sort.by(Sort.Direction.DESC, "ratingAvg");
            case "popularity" -> Sort.by(Sort.Direction.DESC, "ratingCount");
            case "newest" -> Sort.by(Sort.Direction.DESC, "createdAt");
            default -> Sort.by(Sort.Direction.DESC, "createdAt");
        };
    }

    /** Used by cart/checkout services to resolve products defensively. */
    @Transactional(readOnly = true)
    public Product requireActive(Long productId) {
        Optional<Product> product = productRepository.findByIdAndIsDeletedFalse(productId);
        if (product.isEmpty() || !product.get().getIsActive()) {
            throw new BusinessException(404, "PRODUCT_UNAVAILABLE", "Requested product is not available");
        }
        return product.get();
    }
}
