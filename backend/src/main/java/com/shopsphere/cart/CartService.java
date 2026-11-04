package com.shopsphere.cart;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopsphere.cart.entity.Cart;
import com.shopsphere.cart.entity.CartItem;
import com.shopsphere.cart.repository.CartItemRepository;
import com.shopsphere.cart.repository.CartRepository;
import com.shopsphere.common.exception.BusinessException;
import com.shopsphere.common.exception.ResourceNotFound;
import com.shopsphere.inventory.repository.InventoryRepository;
import com.shopsphere.product.ProductService;
import com.shopsphere.product.entity.Product;
import com.shopsphere.user.repository.UserRepository;

/**
 * Shopping-cart behaviour. Server computes every price;
 * stock vs available inventory enforced on every mutation.
 */
@Service
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final UserRepository userRepository;
    private final InventoryRepository inventoryRepository;
    private final ProductService productService;

    public CartService(CartRepository cartRepository,
                       CartItemRepository cartItemRepository,
                       UserRepository userRepository,
                       InventoryRepository inventoryRepository,
                       ProductService productService) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.userRepository = userRepository;
        this.inventoryRepository = inventoryRepository;
        this.productService = productService;
    }

    @Transactional(readOnly = true)
    public CartDto getCartDto(Long userId) {
        Cart cart = getOrCreateCart(userId);
        List<CartItem> items = cartItemRepository.findByCartId(cart.getId());
        List<CartItemDto> itemDtos = items.stream()
                .map(item -> {
                    Product product = item.getProduct();
                    int available = inventoryRepository.findByProductId(product.getId())
                            .map(inv -> inv.availableQuantity()).orElse(0);
                    double unit = product.getSellingPrice().doubleValue();
                    return new CartItemDto(
                            item.getId(),
                            product.getId(),
                            product.getName(),
                            product.getSlug(),
                            primaryImage(product),
                            unit,
                            unit * item.getQuantity(),
                            item.getQuantity(),
                            available,
                            item.getQuantity() <= available);
                })
                .toList();
        BigDecimal subtotal = items.stream()
                .map(item -> item.getProduct().getSellingPrice()
                        .multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CartDto(itemDtos, subtotal.doubleValue(), items.size());
    }

    @Transactional
    public CartDto addItem(Long userId, Long productId, int quantity) {
        if (quantity < 1 || quantity > CartItem.MAX_QUANTITY_PER_ITEM) {
            throw BusinessException.badRequest("INVALID_QUANTITY",
                    "Quantity must be between 1 and " + CartItem.MAX_QUANTITY_PER_ITEM);
        }
        Cart cart = getOrCreateCart(userId);
        Product product = productService.requireActive(productId);

        int available = inventoryRepository.findByProductId(productId)
                .map(inv -> inv.availableQuantity())
                .orElse(0);

        CartItem item = cartItemRepository.findByCartIdAndProductId(cart.getId(), productId)
                .orElse(null);
        int existing = item == null ? 0 : item.getQuantity();
        if (existing + quantity > available) {
            throw BusinessException.conflict("OUT_OF_STOCK",
                    "Only " + Math.max(0, available - existing) + " more unit(s) available for this product");
        }

        if (item == null) {
            item = CartItem.builder()
                    .product(product)
                    .quantity(quantity)
                    .build();
            item.attachCart(cart);
            cartItemRepository.save(item);
        } else {
            item.setQuantity(existing + quantity);
        }
        return getCartDto(userId);
    }

    @Transactional
    public CartDto updateItem(Long userId, Long itemId, int quantity) {
        Cart cart = getOrCreateCart(userId);
        CartItem item = cartItemRepository.findByIdAndCartId(itemId, cart.getId())
                .orElseThrow(() -> ResourceNotFound.of("Cart item"));
        if (quantity < 1 || quantity > CartItem.MAX_QUANTITY_PER_ITEM) {
            throw BusinessException.badRequest("INVALID_QUANTITY",
                    "Quantity must be between 1 and " + CartItem.MAX_QUANTITY_PER_ITEM);
        }
        int available = inventoryRepository.findByProductId(item.getProduct().getId())
                .map(inv -> inv.availableQuantity())
                .orElse(0);
        if (quantity > available) {
            throw BusinessException.conflict("OUT_OF_STOCK",
                    "Only " + available + " unit(s) of this product are available");
        }
        item.setQuantity(quantity);
        return getCartDto(userId);
    }

    @Transactional
    public void removeItem(Long userId, Long itemId) {
        Cart cart = getOrCreateCart(userId);
        CartItem item = cartItemRepository.findByIdAndCartId(itemId, cart.getId())
                .orElseThrow(() -> ResourceNotFound.of("Cart item"));
        cartItemRepository.delete(item);
    }

    @Transactional
    public void clearCart(Long userId) {
        cartItemRepository.deleteByCartId(getOrCreateCart(userId).getId());
    }

    private Cart getOrCreateCart(Long userId) {
        return cartRepository.findByUserId(userId).orElseGet(() ->
                cartRepository.save(Cart.forUser(userRepository.getReferenceById(userId))));
    }

    private String primaryImage(Product product) {
        return product.getImages().stream()
                .filter(img -> Boolean.TRUE.equals(img.getIsPrimary()))
                .map(img -> img.getImageUrl())
                .findFirst()
                .orElse(product.getImages().stream().findFirst()
                        .map(img -> img.getImageUrl()).orElse(null));
    }
}
