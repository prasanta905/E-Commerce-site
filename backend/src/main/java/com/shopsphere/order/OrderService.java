package com.shopsphere.order;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopsphere.common.exception.BusinessException;
import com.shopsphere.common.exception.ResourceNotFound;
import com.shopsphere.inventory.entity.InventoryTransactionType;
import com.shopsphere.inventory.repository.InventoryRepository;
import com.shopsphere.inventory.repository.InventoryTransactionRepository;
import com.shopsphere.notification.NotificationSaver;
import com.shopsphere.order.entity.Order;
import com.shopsphere.order.entity.OrderItem;
import com.shopsphere.order.entity.OrderStatus;
import com.shopsphere.order.entity.OrderStatusHistory;
import com.shopsphere.order.entity.ReturnRequest;
import com.shopsphere.order.entity.ReturnStatus;
import com.shopsphere.order.repository.OrderRepository;
import com.shopsphere.order.repository.OrderStatusHistoryRepository;
import com.shopsphere.order.repository.ReturnRequestRepository;
import com.shopsphere.order.repository.ShipmentRepository;

/**
 * Customer-scoped order service. All reads are by (order, user) pairs so another
 * user's order can never be read or mutated. Status writes go through the
 * OrderStatus FSM rules encoded on the enum.
 */
@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository statusHistoryRepository;
    private final ShipmentRepository shipmentRepository;
    private final ReturnRequestRepository returnRequestRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;
    private final NotificationSaver notificationSaver;
    private final ObjectMapper objectMapper;

    public OrderService(OrderRepository orderRepository,
                        OrderStatusHistoryRepository statusHistoryRepository,
                        ShipmentRepository shipmentRepository,
                        ReturnRequestRepository returnRequestRepository,
                        InventoryRepository inventoryRepository,
                        InventoryTransactionRepository inventoryTransactionRepository,
                        NotificationSaver notificationSaver,
                        ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.shipmentRepository = shipmentRepository;
        this.returnRequestRepository = returnRequestRepository;
        this.inventoryRepository = inventoryRepository;
        this.inventoryTransactionRepository = inventoryTransactionRepository;
        this.notificationSaver = notificationSaver;
        this.objectMapper = objectMapper;
    }

    // ------------------------------------------------------------- reads
    @Transactional(readOnly = true)
    public Page<OrderSummaryDto> listForUser(Long userId, int page, int size) {
        Page<Order> orders = orderRepository.findByUserIdOrderByCreatedAtDesc(userId,
                PageRequest.of(Math.max(0, page), Math.max(1, Math.min(60, size))));
        return orders.map(this::toSummary);
    }

    @Transactional(readOnly = true)
    public OrderDetailDto detailForUser(Long orderId, Long userId) {
        return toDetail(ownedOrder(orderId, userId));
    }

    // ------------------------------------------------------------ cancel
    @Transactional
    public void cancel(Long orderId, Long userId, String reason) {
        Order order = ownedOrder(orderId, userId);
        OrderStatus from = order.getStatus();
        if (!from.isCancellable()) {
            throw BusinessException.conflict("INVALID_STATE",
                    "Order in status " + from + " cannot be cancelled. Only PENDING/CONFIRMED orders may be.");
        }

        for (OrderItem item : order.getItems()) {
            if (from == OrderStatus.PENDING) {
                // Reservation was still on hold — just release it.
                int released = inventoryRepository.release(item.getProduct().getId(), item.getQuantity());
                if (released > 0) {
                    logStockMovement(item, InventoryTransactionType.RELEASE, item.getQuantity(),
                            order.getId(), userId, "Reservation released on customer cancel (" + reason + ")");
                }
            } else {
                // CONFIRMED — units were committed at payment; put them back.
                inventoryRepository.restock(item.getProduct().getId(), item.getQuantity());
                logStockMovement(item, InventoryTransactionType.RETURN, item.getQuantity(),
                        order.getId(), userId, "Stock restored on customer cancel (" + reason + ")");
            }
        }

        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelReason(reason == null || reason.isBlank() ? "Cancelled by customer" : reason);
        orderRepository.save(order);

        appendHistory(order, from, "customer", "Cancelled by customer" +
                (reason == null || reason.isBlank() ? "" : (": " + reason)));
        notificationSaver.orderCreated(userId,
                "Order " + order.getOrderNumber() + " cancelled",
                "Status: CANCELLED. Any paid amount will be refunded per policy.");
    }

    // ---------------------------------------------------------- returns
    @Transactional
    public void requestReturn(Long orderId, Long userId, String reason) {
        Order order = ownedOrder(orderId, userId);
        if (!order.getStatus().isReturnable()) {
            throw BusinessException.conflict("INVALID_STATE", "Only DELIVERED orders may be returned");
        }
        if (returnRequestRepository.findByOrderId(orderId).isPresent()) {
            throw BusinessException.conflict("RETURN_EXISTS", "A return request already exists for this order");
        }
        ReturnRequest request = ReturnRequest.builder()
                .order(order)
                .user(order.getUser())
                .reason(reason)
                .status(ReturnStatus.REQUESTED)
                .build();
        returnRequestRepository.save(request);

        OrderStatus from = order.getStatus();
        order.setStatus(OrderStatus.RETURN_REQUESTED);
        orderRepository.save(order);
        appendHistory(order, from, "customer", "Return requested" +
                (reason == null || reason.isBlank() ? "" : (": " + reason)));
    }

    // --------------------------------------------------------- mapping
    OrderSummaryDto toSummary(Order order) {
        List<OrderSummaryDto.ItemLine> items = order.getItems().stream()
                .map(i -> new OrderSummaryDto.ItemLine(
                        i.getProduct().getId(), i.getProductName(), i.getProductImageUrl(),
                        i.getQuantity(), i.getUnitPrice().doubleValue()))
                .toList();
        return new OrderSummaryDto(order.getId(), order.getOrderNumber(), order.getStatus().name(),
                order.getPaymentMethod().name(), order.getTotalAmount(), order.getCreatedAt(), items);
    }

    OrderDetailDto toDetail(Order order) {
        List<OrderStatusHistory> history =
                statusHistoryRepository.findByOrderIdOrderByCreatedAtAsc(order.getId());

        String tracking = shipmentRepository.findByOrderId(order.getId())
                .map(s -> s.getTrackingNumber()).orElse(null);
        String courier = shipmentRepository.findByOrderId(order.getId())
                .map(s -> s.getCourier()).orElse(null);

        String paymentStatus = switch (order.getStatus()) {
            case PENDING -> order.getPaymentMethod() == com.shopsphere.order.entity.PaymentMethod.COD
                    ? "PENDING" : "PENDING";
            case CONFIRMED, PROCESSING, SHIPPED, OUT_FOR_DELIVERY,
                 DELIVERED, RETURN_REQUESTED, RETURNED -> "PAID";
            case CANCELLED -> "REFUND_PENDING";
        };

        return new OrderDetailDto(
                order.getId(),
                order.getOrderNumber(),
                order.getStatus().name(),
                order.getPaymentMethod().name(),
                paymentStatus,
                parseAddress(order.getAddressSnapshot()),
                order.getSubtotal(), order.getDiscountAmount(), order.getShippingFee(),
                order.getTotalAmount(),
                order.getCreatedAt(),
                order.getCancelReason(),
                tracking, courier,
                order.getItems().stream().map(i -> new OrderDetailDto.ItemLine(
                        i.getProduct().getId(), i.getProductName(), i.getProductImageUrl(),
                        i.getQuantity(), i.getUnitPrice().doubleValue())).toList(),
                history.stream().map(h -> new OrderDetailDto.StatusEvent(
                        h.getPreviousStatus() == null ? null : h.getPreviousStatus().name(),
                        h.getNewStatus().name(), h.getChangedBy(), h.getNote(), h.getCreatedAt())).toList(),
                order.getCoupon() == null ? List.of()
                        : List.of(new OrderDetailDto.CouponEvent(order.getCoupon().getCode())));
    }

    // ----------------------------------------------------------- internals
    Order ownedOrder(Long orderId, Long userId) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> ResourceNotFound.of("Order"));
        if (!order.getUser().getId().equals(userId)) {
            // Deliberately 404 — never hint at other users' resources
            throw ResourceNotFound.of("Order");
        }
        return order;
    }

    private void appendHistory(Order order, OrderStatus from, String actor, String note) {
        OrderStatusHistory historyRow = OrderStatusHistory.builder()
                .previousStatus(from)
                .newStatus(order.getStatus())
                .changedBy(actor)
                .note(note)
                .build();
        historyRow.attachTo(order);
        statusHistoryRepository.save(historyRow);
    }

    private void logStockMovement(OrderItem item, InventoryTransactionType type, int delta,
                                  Long orderId, Long userId, String note) {
        inventoryRepository.findByProductId(item.getProduct().getId()).ifPresent(inventory ->
                inventoryTransactionRepository.save(com.shopsphere.inventory.entity.InventoryTransaction.builder()
                        .inventory(inventory)
                        .type(type)
                        .quantityDelta(delta)
                        .note(note + " (order " + orderId + ")")
                        .orderId(orderId)
                        .userId(userId)
                        .build()));
    }

    @SuppressWarnings("unchecked")
    private java.util.Map<String, String> parseAddress(String snapshot) {
        try {
            return objectMapper.readValue(snapshot, java.util.Map.class);
        } catch (Exception e) {
            return java.util.Map.of();
        }
    }
}
