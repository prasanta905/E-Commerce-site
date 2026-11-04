package com.shopsphere.cart;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shopsphere.common.ApiResponse;
import com.shopsphere.common.exception.BusinessException;
import com.shopsphere.security.CurrentUser;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Tag(name = "Cart")
@RestController
@RequestMapping("/api/v1/cart")
public class CartController {

    private final CartService cartService;
    private final CurrentUser currentUser;

    public CartController(CartService cartService, CurrentUser currentUser) {
        this.cartService = cartService;
        this.currentUser = currentUser;
    }

    public record AddItemRequest(@NotNull @Positive Long productId,
                                 @Min(1) @Max(10) int quantity) {}

    public record UpdateItemRequest(@Min(1) @Max(10) int quantity) {}

    @GetMapping
    public ResponseEntity<ApiResponse<CartDto>> getCart() {
        return ResponseEntity.ok(ApiResponse.ok(cartService.getCartDto(currentUser.requireUserId())));
    }

    @PostMapping("/items")
    public ResponseEntity<ApiResponse<CartDto>> addItem(@NotNull @org.springframework.validation.annotation.Validated @jakarta.validation.Valid @RequestBody AddItemRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                cartService.addItem(currentUser.requireUserId(), request.productId(), request.quantity())));
    }

    @PutMapping("/items/{itemId}")
    public ResponseEntity<ApiResponse<CartDto>> updateItem(@PathVariable Long itemId,
                                                           @jakarta.validation.Valid @RequestBody UpdateItemRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                cartService.updateItem(currentUser.requireUserId(), itemId, request.quantity())));
    }

    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<Void> removeItem(@PathVariable Long itemId) {
        cartService.removeItem(currentUser.requireUserId(), itemId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> clear() {
        cartService.clearCart(currentUser.requireUserId());
        return ResponseEntity.noContent().build();
    }
}
