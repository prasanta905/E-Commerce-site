package com.shopsphere.review;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopsphere.common.PageResponse;
import com.shopsphere.common.exception.BusinessException;
import com.shopsphere.common.exception.ResourceNotFound;
import com.shopsphere.notification.NotificationSaver;
import com.shopsphere.order.entity.Order;
import com.shopsphere.order.entity.OrderStatus;
import com.shopsphere.order.repository.OrderRepository;
import com.shopsphere.product.entity.Product;
import com.shopsphere.product.repository.ProductRepository;
import com.shopsphere.review.entity.Review;
import com.shopsphere.review.repository.ReviewRepository;
import com.shopsphere.user.repository.UserRepository;

/**
 * Product reviews. A review is verified-purchase when the author has a
 * DELIVERED order containing the product; post-delivery reviews can link to
 * the newest such order. One review per (product, user) — DB-enforced.
 */
@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final NotificationSaver notificationSaver;
    private final Object reviewLock = new Object();

    public ReviewService(ReviewRepository reviewRepository,
                         ProductRepository productRepository,
                         UserRepository userRepository,
                         OrderRepository orderRepository,
                         NotificationSaver notificationSaver) {
        this.reviewRepository = reviewRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
        this.notificationSaver = notificationSaver;
    }

    // ------------------------------------------------------------ reads
    @Transactional(readOnly = true)
    public PagedReviews listForProduct(Long productId, int page, int size) {
        Page<Review> reviews = reviewRepository.findByProductIdOrderByCreatedAtDesc(productId,
                PageRequest.of(Math.max(0, page), Math.max(1, Math.min(60, size))));
        ReviewRepository.RatingAggregate agg = reviewRepository.aggregateForProduct(productId);
        Double avg = agg != null ? agg.getAvg() : null;
        Long count = agg != null ? agg.getCount() : 0L;
        return new PagedReviews(
                reviews.getContent().stream().map(this::toDto).toList(),
                PageResponse.from(reviews),
                avg == null ? null : Math.round(avg * 10.0) / 10.0,
                count == null ? 0 : count);
    }

    // ---------------------------------------------------------- writes
    @Transactional
    public ReviewDto create(Long userId, Long productId, ReviewUpsertRequest request) {
        synchronized (reviewLock) {
            if (request.rating() == null || request.rating() < 1 || request.rating() > 5) {
                throw BusinessException.badRequest("RATING_INVALID", "Rating must be between 1 and 5");
            }
            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> ResourceNotFound.of("Product"));
            if (reviewRepository.existsByProductIdAndUserId(productId, userId)) {
                throw BusinessException.conflict("REVIEW_EXISTS",
                        "You have already reviewed this product — edit your review instead");
            }

            Order delivered = firstDelivered(userId, productId);
            Review review = Review.builder()
                    .product(product)
                    .user(userRepository.getReferenceById(userId))
                    .order(delivered)
                    .rating(request.rating())
                    .title(trim(request.title(), 120))
                    .body(request.body())
                    .isVerified(delivered != null)
                    .build();
            reviewRepository.save(review);

            notificationSaver.orderCreated(userId, "Review published",
                    "Thanks for reviewing " + product.getName() + "!");
            return toDto(review);
        }
    }

    @Transactional
    public ReviewDto update(Long userId, Long productId, Long reviewId, ReviewUpsertRequest request) {
        if (request.rating() == null || request.rating() < 1 || request.rating() > 5) {
            throw BusinessException.badRequest("RATING_INVALID", "Rating must be between 1 and 5");
        }
        Review review = ownedReview(userId, productId, reviewId);
        review.setRating(request.rating());
        review.setTitle(trim(request.title(), 120));
        review.setBody(request.body());
        return toDto(reviewRepository.save(review));
    }

    @Transactional
    public void delete(Long userId, Long productId, Long reviewId) {
        reviewRepository.delete(ownedReview(userId, productId, reviewId));
    }

    // ------------------------------------------------------- internals
    private Order firstDelivered(Long userId, Long productId) {
        List<Order> delivered = orderRepository.findDeliveredByUserAndProduct(userId, productId);
        return delivered.stream().max(java.util.Comparator.comparing(Order::getCreatedAt)).orElse(null);
    }

    private Review ownedReview(Long userId, Long productId, Long reviewId) {
        Review review = reviewRepository.findByIdAndProductId(reviewId, productId)
                .orElseThrow(() -> ResourceNotFound.of("Review"));
        if (!review.getUser().getId().equals(userId)) {
            throw new BusinessException(403, "FORBIDDEN", "You can only manage your own reviews");
        }
        return review;
    }

    private String trim(String s, int max) {
        if (s == null) return null;
        s = s.trim();
        return s.length() > max ? s.substring(0, max) : s;
    }

    ReviewDto toDto(Review review) {
        return new ReviewDto(review.getId(), review.getProduct().getId(),
                review.getUser().getFullName(), review.getRating(), review.getTitle(),
                review.getBody(), Boolean.TRUE.equals(review.getIsVerified()), review.getCreatedAt());
    }

    public record PagedReviews(List<ReviewDto> items, PageResponse meta, Double averageRating, Long totalCount) {}
}
