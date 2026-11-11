package com.shopsphere.product;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import com.shopsphere.brand.entity.Brand;
import com.shopsphere.category.entity.Category;
import com.shopsphere.common.PageResponse;
import com.shopsphere.inventory.entity.Inventory;
import com.shopsphere.product.entity.Product;
import com.shopsphere.product.entity.ProductImage;
import com.shopsphere.product.entity.ProductSpecification;

/**
 * Entity ⇒ DTO mapping (never leak entities over the wire).
 */
@Component
public class ProductMapper {

    public ProductCardDto toCard(Product p, Inventory inventory) {
        Inventory inv = inventory != null ? inventory : p.getInventory();
        int available = inv == null ? 0 : inv.availableQuantity();
        return new ProductCardDto(
            p.getId(), p.getName(), p.getSlug(), p.getSellingPrice(), p.getMrp(),
            ProductCardDto.discountPercent(p.getMrp(), p.getSellingPrice()),
            p.getRatingAvg().doubleValue(), p.getRatingCount(),
            primaryImage(p), categoryName(p), brandName(p),
            available > 0,
            p.getCreatedAt() != null && p.getCreatedAt().isAfter(java.time.Instant.now().minusSeconds(60 * 60 * 24 * 14))
        );
    }

    public ProductDetailDto toDetail(Product p, List<ProductImage> images,
                                     List<ProductSpecification> specs, Inventory inventory,
                                     List<Product> related) {
        int available = inventory == null ? 0 : inventory.availableQuantity();
        return new ProductDetailDto(
            p.getId(), p.getName(), p.getSlug(), p.getDescription(),
            p.getSellingPrice(), p.getMrp(),
            ProductCardDto.discountPercent(p.getMrp(), p.getSellingPrice()),
            p.getRatingAvg().doubleValue(), p.getRatingCount(),
            images.stream().map(ProductImage::getImageUrl).toList(),
            specs.stream().collect(Collectors.toMap(
                    ProductSpecification::getSpecKey,
                    ProductSpecification::getSpecValue,
                    (a, b) -> b)),
            p.getCategory().getId(), categoryName(p),
            p.getBrand().getId(), brandName(p),
            p.getIsFeatured(), available,
            inventory == null ? 0 : inventory.getLowStockThreshold(),
            available > 0,
            related.stream().map(r -> toCard(r, null)).toList()
        );
    }

    public ProductListResponse toListResponse(Page<Product> page) {
        List<ProductCardDto> items = page.getContent().stream().map(p -> toCard(p, p.getInventory())).toList();
        return new ProductListResponse(items, PageResponse.from(page));
    }

    private String primaryImage(Product p) {
        return p.getImages().stream()
                .sorted(java.util.Comparator.comparing(ProductImage::getSortOrder))
                .map(ProductImage::getImageUrl)
                .findFirst().orElse(null);
    }

    private String categoryName(Product p) {
        Category c = p.getCategory();
        return c == null ? null : c.getName();
    }

    private String brandName(Product p) {
        Brand b = p.getBrand();
        return b == null ? null : b.getName();
    }
}
