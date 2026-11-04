package com.shopsphere.wishlist.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopsphere.wishlist.entity.WishlistItem;

public interface WishlistItemRepository extends JpaRepository<WishlistItem, Long> {
    boolean existsByWishlistIdAndProductId(Long wishlistId, Long productId);
    Optional<WishlistItem> findByWishlistIdAndProductId(Long wishlistId, Long productId);
    java.util.List<WishlistItem> findAllByWishlistIdOrderByAddedAtDesc(Long wishlistId);
    void deleteByWishlistIdAndProductId(Long wishlistId, Long productId);
    void deleteByWishlistId(Long wishlistId);
}
