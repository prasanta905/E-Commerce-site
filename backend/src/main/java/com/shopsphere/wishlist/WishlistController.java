package com.shopsphere.wishlist;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shopsphere.common.ApiResponse;
import com.shopsphere.security.CurrentUser;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;

@Tag(name = "Wishlist")
@RestController
@RequestMapping("/api/v1/wishlist")
public class WishlistController {

    private final WishlistService wishlistService;
    private final CurrentUser currentUser;

    public WishlistController(WishlistService wishlistService, CurrentUser currentUser) {
        this.wishlistService = wishlistService;
        this.currentUser = currentUser;
    }

    public record AddItemRequest(@NotNull Long productId) {}

    @GetMapping
    public ResponseEntity<ApiResponse<WishlistResponse>> get() {
        return ResponseEntity.ok(ApiResponse.ok(wishlistService.getFor(currentUser.requireUserId())));
    }

    @PostMapping("/items")
    public ResponseEntity<ApiResponse<WishlistResponse>> add(@jakarta.validation.Valid @RequestBody AddItemRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                wishlistService.addProduct(currentUser.requireUserId(), request.productId())));
    }

    @DeleteMapping("/items/{productId}")
    public ResponseEntity<Void> remove(@PathVariable Long productId) {
        wishlistService.removeProduct(currentUser.requireUserId(), productId);
        return ResponseEntity.noContent().build();
    }
}
