package com.shopsphere.wishlist;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopsphere.common.exception.BusinessException;
import com.shopsphere.inventory.repository.InventoryRepository;
import com.shopsphere.product.ProductCardDto;
import com.shopsphere.product.ProductMapper;
import com.shopsphere.product.ProductService;
import com.shopsphere.user.repository.UserRepository;
import com.shopsphere.wishlist.entity.Wishlist;
import com.shopsphere.wishlist.entity.WishlistItem;
import com.shopsphere.wishlist.repository.WishlistItemRepository;
import com.shopsphere.wishlist.repository.WishlistRepository;

/**
 * Wishlist: per-user, duplicates prevented by UNIQUE(wishlist_id, product_id)
 * — application also short-circuits for a nicer error.
 */
@Service
public class WishlistService {

    private final WishlistRepository wishlistRepository;
    private final WishlistItemRepository wishlistItemRepository;
    private final UserRepository userRepository;
    private final ProductService productService;
    private final ProductMapper productMapper;
    private final InventoryRepository inventoryRepository;

    public WishlistService(WishlistRepository wishlistRepository,
                           WishlistItemRepository wishlistItemRepository,
                           UserRepository userRepository,
                           ProductService productService,
                           ProductMapper productMapper,
                           InventoryRepository inventoryRepository) {
        this.wishlistRepository = wishlistRepository;
        this.wishlistItemRepository = wishlistItemRepository;
        this.userRepository = userRepository;
        this.productService = productService;
        this.productMapper = productMapper;
        this.inventoryRepository = inventoryRepository;
    }

    @Transactional(readOnly = true)
    public WishlistResponse getFor(Long userId) {
        Wishlist wishlist = getOrCreate(userId);
        List<ProductCardDto> products = wishlistItemRepository
                .findAllByWishlistIdOrderByAddedAtDesc(wishlist.getId()).stream()
                .map(WishlistItem::getProduct)
                .map(product -> productMapper.toCard(product,
                        inventoryRepository.findByProductId(product.getId()).orElse(null)))
                .toList();
        return new WishlistResponse(products);
    }

    @Transactional
    public WishlistResponse addProduct(Long userId, Long productId) {
        Wishlist wishlist = getOrCreate(userId);
        var product = productService.requireActive(productId);

        if (wishlistItemRepository.existsByWishlistIdAndProductId(wishlist.getId(), productId)) {
            // idempotent user intent; NOT an error — the frontend optimistically acts the same.
            return getFor(userId);
        }
        WishlistItem item = WishlistItem.builder()
                .wishlist(wishlist)
                .product(product)
                .build();
        wishlistItemRepository.save(item);
        return getFor(userId);
    }

    @Transactional
    public void removeProduct(Long userId, Long productId) {
        Wishlist wishlist = getOrCreate(userId);
        if (!wishlistItemRepository.existsByWishlistIdAndProductId(wishlist.getId(), productId)) {
            throw BusinessException.notFound("Wishlist item");
        }
        wishlistItemRepository.deleteByWishlistIdAndProductId(wishlist.getId(), productId);
    }

    private Wishlist getOrCreate(Long userId) {
        return wishlistRepository.findByUserId(userId).orElseGet(() ->
                wishlistRepository.save(Wishlist.forUser(userRepository.getReferenceById(userId))));
    }
}