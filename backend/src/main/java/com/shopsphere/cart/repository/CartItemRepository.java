package com.shopsphere.cart.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopsphere.cart.entity.CartItem;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    List<CartItem> findByCartId(Long cartId);
    List<CartItem> findAllByCartUserId(Long userId);
    java.util.List<CartItem> findAllByCartUserIdOrderByProductAsc(Long userId);
    Optional<CartItem> findByIdAndCartId(Long itemId, Long cartId);
    Optional<CartItem> findByCartIdAndProductId(Long cartId, Long productId);
    void deleteByCartId(Long cartId);
    long countByCartId(Long cartId);
}
