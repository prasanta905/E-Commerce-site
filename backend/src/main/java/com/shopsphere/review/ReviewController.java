package com.shopsphere.review;

import com.shopsphere.common.ApiResponse;
import com.shopsphere.security.CurrentUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Reviews", description = "Product reviews with verified-purchase badge")
@RestController
@RequestMapping("/api/v1/products/{productId}/reviews")
public class ReviewController {

    private final ReviewService reviewService;
    private final CurrentUser currentUser;

    public ReviewController(ReviewService reviewService, CurrentUser currentUser) {
        this.reviewService = reviewService;
        this.currentUser = currentUser;
    }

    public record ReviewDeleteResponse(String message) {}

    @Operation(summary = "Paged reviews for a product with the live average rating")
    @GetMapping
    public ResponseEntity<ApiResponse<ReviewService.PagedReviews>> list(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.ok(reviewService.listForProduct(productId, page, size)));
    }

    @Operation(summary = "Create a review (verified-purchase badge applied automatically)")
    @PostMapping
    public ResponseEntity<ApiResponse<ReviewDto>> create(
            @PathVariable Long productId, @Valid @RequestBody ReviewUpsertRequest request) {
        ReviewDto dto = reviewService.create(currentUser.requireUserId(), productId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(dto));
    }

    @Operation(summary = "Edit your own review")
    @PatchMapping("/{reviewId}")
    public ResponseEntity<ApiResponse<ReviewDto>> update(
            @PathVariable Long productId, @PathVariable Long reviewId,
            @Valid @RequestBody ReviewUpsertRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                reviewService.update(currentUser.requireUserId(), productId, reviewId, request)));
    }

    @Operation(summary = "Delete your own review")
    @DeleteMapping("/{reviewId}")
    public ResponseEntity<ApiResponse<ReviewDeleteResponse>> delete(
            @PathVariable Long productId, @PathVariable Long reviewId) {
        reviewService.delete(currentUser.requireUserId(), productId, reviewId);
        return ResponseEntity.ok(ApiResponse.ok(new ReviewDeleteResponse("Review deleted")));
    }
}
