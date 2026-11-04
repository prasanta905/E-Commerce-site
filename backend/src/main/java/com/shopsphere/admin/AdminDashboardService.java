package com.shopsphere.admin;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopsphere.common.exception.BadRequest;
import com.shopsphere.inventory.repository.InventoryRepository;
import com.shopsphere.order.entity.Order;
import com.shopsphere.order.entity.OrderStatus;
import com.shopsphere.order.entity.ReturnStatus;
import com.shopsphere.order.repository.OrderRepository;
import com.shopsphere.order.repository.ReturnRequestRepository;
import com.shopsphere.product.repository.ProductRepository;
import com.shopsphere.user.repository.UserRepository;

/**
 * Admin dashboard + analytics. Numbers are computed from live tables — no
 * fake data. Analytics buckets aggregated in Java to stay portable across the
 * daily/weekly/monthly/yearly periods without vendor SQL functions.
 */
@Service
public class AdminDashboardService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final ReturnRequestRepository returnRequestRepository;

    public AdminDashboardService(OrderRepository orderRepository,
                                 UserRepository userRepository,
                                 ProductRepository productRepository,
                                 InventoryRepository inventoryRepository,
                                 ReturnRequestRepository returnRequestRepository) {
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
        this.returnRequestRepository = returnRequestRepository;
    }

    @Transactional(readOnly = true)
    public DashboardDto dashboard() {
        Instant dayStart = LocalDate.now(IST).atStartOfDay(IST).toInstant();
        Instant monthStart = LocalDate.now(IST).withDayOfMonth(1).atStartOfDay(IST).toInstant();

        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (OrderStatus status : OrderStatus.values()) {
            byStatus.put(status.name(), orderRepository.countByStatus(status));
        }

        long pendingReturns = returnRequestRepository.countByStatus(ReturnStatus.REQUESTED);
        long lowStock = inventoryRepository.findLowStock().size();

        return new DashboardDto(
                userRepository.countByIsDeletedFalse(),
                userRepository.countByIsDeletedFalseAndCreatedAtGreaterThanEqual(dayStart),
                productRepository.countByIsDeletedFalse(),
                orderRepository.countByStatusAndCreatedAtAfter(OrderStatus.PENDING, dayStart)
                        + orderRepository.countByStatusAndCreatedAtAfter(OrderStatus.CONFIRMED, dayStart)
                        + orderRepository.countByStatusAndCreatedAtAfter(OrderStatus.PROCESSING, dayStart)
                        + orderRepository.countByStatusAndCreatedAtAfter(OrderStatus.SHIPPED, dayStart)
                        + orderRepository.countByStatusAndCreatedAtAfter(OrderStatus.OUT_FOR_DELIVERY, dayStart)
                        + orderRepository.countByStatusAndCreatedAtAfter(OrderStatus.DELIVERED, dayStart),
                orderRepository.sumRevenueTotal(),
                orderRepository.sumRevenueSince(dayStart),
                orderRepository.sumRevenueSince(monthStart),
                byStatus,
                lowStock,
                pendingReturns);
    }

    @Transactional(readOnly = true)
    public AnalyticsDto analytics(String period, Instant from, Instant to) {
        Instant effectiveFrom = from != null ? from : LocalDate.now(IST).minusDays(90).atStartOfDay(IST).toInstant();
        Instant effectiveTo = to != null ? to : Instant.now();

        List<Order> orders = orderRepository
                .findByCreatedAtGreaterThanEqualOrderByCreatedAtAsc(effectiveFrom)
                .stream()
                .filter(o -> !o.getCreatedAt().isAfter(effectiveTo))
                .toList();

        Map<String, Bucket> buckets = new LinkedHashMap<>();
        for (Order o : orders) {
            if (o.getStatus() == OrderStatus.CANCELLED) continue;
            String key = bucketKey(o.getCreatedAt(), period);
            Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket());
            bucket.orders++;
            bucket.revenue = bucket.revenue.add(o.getTotalAmount());
        }

        List<AnalyticsPointDto> points = new ArrayList<>();
        buckets.forEach((key, bucket) -> points.add(new AnalyticsPointDto(key, bucket.orders, bucket.revenue)));

        long ordersCount = orders.stream().filter(o -> o.getStatus() != OrderStatus.CANCELLED).count();
        double averageOrderValue = ordersCount == 0 ? 0.0
                : points.stream().mapToDouble(p -> p.revenue() == null ? 0 : p.revenue().doubleValue()).sum()
                        / ordersCount;

        return new AnalyticsDto(period, effectiveFrom, effectiveTo, points, averageOrderValue,
                orders.stream().mapToDouble(o -> o.getTotalAmount() == null ? 0
                        : o.getTotalAmount().doubleValue()).sum());
    }

    private String bucketKey(Instant at, String period) {
        LocalDate day = at.atZone(IST).toLocalDate();
        return switch (period == null ? "daily" : period.toLowerCase()) {
            case "weekly" -> day.with(java.time.DayOfWeek.MONDAY).toString() + " (week)";
            case "monthly" -> day.withDayOfMonth(1).toString();
            case "yearly" -> Integer.toString(day.getYear());
            case "daily" -> day.toString();
            default -> throw BadRequest.of("PERIOD_INVALID", "period must be daily|weekly|monthly|yearly");
        };
    }

    public record DashboardDto(
            long totalCustomers,
            long newCustomersToday,
            long activeProducts,
            long ordersToday,
            BigDecimal revenueTotal,
            BigDecimal revenueToday,
            BigDecimal revenueMonth,
            Map<String, Long> ordersByStatus,
            long lowStockCount,
            long pendingReturns) {}

    public record AnalyticsDto(String period, Instant from, Instant to,
                               List<AnalyticsPointDto> points,
                               double averageOrderValue, double totalRevenue) {}

    public record AnalyticsPointDto(String bucket, long orders, BigDecimal revenue) {}

    private static final class Bucket {
        long orders;
        BigDecimal revenue = BigDecimal.ZERO;
    }
}
