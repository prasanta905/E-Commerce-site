package com.shopsphere.checkout;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopsphere.cart.entity.CartItem;
import com.shopsphere.cart.repository.CartItemRepository;
import com.shopsphere.coupon.CouponApplication;
import com.shopsphere.coupon.CouponService;
import com.shopsphere.coupon.entity.CouponRedemption;
import com.shopsphere.coupon.repository.CouponRedemptionRepository;
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
import com.shopsphere.order.entity.PaymentMethod;
import com.shopsphere.order.repository.OrderRepository;
import com.shopsphere.order.repository.OrderStatusHistoryRepository;
import com.shopsphere.payment.PaymentService;
import com.shopsphere.user.entity.Address;
import com.shopsphere.user.repository.AddressRepository;
import com.shopsphere.user.repository.UserRepository;

/**
 * Checkout pipeline (one atomic database transaction):
 *
 * Reserve → order → items snapshot → coupon redemption → payment row
 *  ├─ COD:   order = CONFIRMED, stock committed immediately
 *  └─ RAZORPAY: order = PENDING, stock held until verify/webhook;
 *                failure releases the reservation (PaymentService).
 *
 * Idempotency: the optional client-supplied key is unique-indexed on orders.
 */
@Service
public class CheckoutService {

    /** Static catalogue — documented assumption (A13): shipping rates are code-defined. */
    static final List<ShippingMethod> SHIPPING_METHODS = List.of(
            new ShippingMethod(1L, "STANDARD", "Standard Delivery (3–6 days)", new BigDecimal("49.00"), new BigDecimal("999.00")),
            new ShippingMethod(2L, "EXPRESS", "Express Delivery (1–2 days)", new BigDecimal("149.00"), new BigDecimal("1999.00")));

    private static final Random ORDER_RANDOM = new Random();

    private final CartItemRepository cartItemRepository;
    private final OrderRepository orderRepository;
    private final AddressRepository addressRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;
    private final CouponService couponService;
    private final CouponRedemptionRepository couponRedemptionRepository;
    private final OrderStatusHistoryRepository statusHistoryRepository;
    private final UserRepository userRepository;
    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;
    private final NotificationSaver notificationSaver;

    public CheckoutService(CartItemRepository cartItemRepository,
                           OrderRepository orderRepository,
                           AddressRepository addressRepository,
                           InventoryRepository inventoryRepository,
                           InventoryTransactionRepository inventoryTransactionRepository,
                           CouponService couponService,
                           CouponRedemptionRepository couponRedemptionRepository,
                           OrderStatusHistoryRepository statusHistoryRepository,
                           UserRepository userRepository,
                           PaymentService paymentService,
                           ObjectMapper objectMapper,
                           NotificationSaver notificationSaver) {
        this.cartItemRepository = cartItemRepository;
        this.orderRepository = orderRepository;
        this.addressRepository = addressRepository;
        this.inventoryRepository = inventoryRepository;
        this.inventoryTransactionRepository = inventoryTransactionRepository;
        this.couponService = couponService;
        this.couponRedemptionRepository = couponRedemptionRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.userRepository = userRepository;
        this.paymentService = paymentService;
        this.objectMapper = objectMapper;
        this.notificationSaver = notificationSaver;
    }

    @Transactional(readOnly = true)
    public CheckoutPreviewDto preview(Long userId, Long addressId, Long shippingMethodId, String couponCode) {
        List<String> stockIssues = new ArrayList<>();
        List<CartItem> items = cartItemsOf(userId);
        if (items.isEmpty()) {
            throw BusinessException.badRequest("EMPTY_CART", "Your cart is empty");
        }

        BigDecimal subtotal = BigDecimal.ZERO;
        for (CartItem item : items) {
            var product = item.getProduct();
            int available = inventoryRepository.findByProductId(product.getId())
                    .map(inv -> inv.availableQuantity()).orElse(0);
            if (item.getQuantity() > available) {
                stockIssues.add(product.getName() + ": only " + available + " available (cart wants "
                        + item.getQuantity() + ")");
            } else {
                subtotal = subtotal.add(product.getSellingPrice()
                        .multiply(BigDecimal.valueOf(item.getQuantity())));
            }
        }

        ShippingMethod shipping = requireShipping(shippingMethodId);
        BigDecimal discount = BigDecimal.ZERO;
        if (couponCode != null && !couponCode.isBlank()) {
            discount = couponService.apply(couponCode, subtotal, userId).discountAmount();
        }
        BigDecimal shippingFee = shippingFeeFor(shipping, subtotal);
        BigDecimal total = subtotal.add(shippingFee).subtract(discount).max(BigDecimal.ZERO);

        return new CheckoutPreviewDto(subtotal, discount, shippingFee, total, couponCode, shipping.name(), stockIssues);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CheckoutResponse checkout(Long userId, CheckoutRequest request) {
        // 1. Idempotency guard.
        if (request.idempotencyKey() != null && !request.idempotencyKey().isBlank()) {
            var existing = orderRepository.findByIdempotencyKey(request.idempotencyKey());
            if (existing.isPresent()) {
                Order o = existing.get();
                String gw = (o.getPaymentMethod() == PaymentMethod.RAZORPAY)
                        ? paymentService.gatewayOrderIdFor(o.getId()).orElse(null)
                        : null;
                return new CheckoutResponse(o.getId(), o.getOrderNumber(), o.getStatus().name(), gw, null);
            }
        }

        // 2. Load cart + enforce stock.
        List<CartItem> items = cartItemsOf(userId);
        if (items.isEmpty()) {
            throw BusinessException.badRequest("EMPTY_CART", "Your cart is empty");
        }
        for (CartItem item : items) {
            Long productId = item.getProduct().getId();
            int updated = inventoryRepository.reserve(productId, item.getQuantity());
            if (updated == 0) {
                throw BusinessException.conflict("OUT_OF_STOCK",
                        "Product '" + item.getProduct().getName() + "' just went out of stock");
            }
        }

        // 3. Address = owned by user.
        Address address = addressRepository.findById(request.addressId())
                .filter(a -> a.getUser().getId().equals(userId))
                .orElseThrow(() -> ResourceNotFound.of("Address"));

        // 4. Money (server-computed only).
        BigDecimal subtotal = BigDecimal.ZERO;
        for (CartItem item : items) {
            subtotal = subtotal.add(item.getProduct().getSellingPrice()
                    .multiply(BigDecimal.valueOf(item.getQuantity())));
        }
        ShippingMethod shipping = requireShipping(request.shippingMethodId());
        BigDecimal shippingFee = shippingFeeFor(shipping, subtotal);

        BigDecimal discount = BigDecimal.ZERO;
        com.shopsphere.coupon.entity.Coupon couponEntity = null;
        CouponApplication applied = null;
        if (request.couponCode() != null && !request.couponCode().isBlank()) {
            applied = couponService.apply(request.couponCode(), subtotal, userId);
            discount = applied.discountAmount();
        }

        BigDecimal total = subtotal.add(shippingFee).subtract(discount).max(BigDecimal.ZERO);

        // 5. Create order (PENDING for online, CONFIRMED for COD).
        Order order = Order.builder()
                .user(userRepository.getReferenceById(userId))
                .orderNumber(generateOrderNumber())
                .addressSnapshot(addressSnapshot(address))
                .subtotal(subtotal)
                .discountAmount(discount)
                .shippingFee(shippingFee)
                .totalAmount(total)
                .paymentMethod(PaymentMethod.valueOf(request.paymentMethod()))
                .idempotencyKey(request.idempotencyKey())
                .status(OrderStatus.PENDING)
                .build();
        orderRepository.save(order);

        if (applied != null) {
            couponEntity = couponService.findActiveByCode(applied.code());
            // Global limit — locked count so concurrent checkouts serialise here.
            if (couponEntity.getMaxGlobalUses() != null
                    && couponRedemptionRepository.countByCouponIdForUpdate(couponEntity.getId())
                            >= couponEntity.getMaxGlobalUses()) {
                throw BusinessException.conflict("COUPON_EXHAUSTED",
                        "This coupon has reached its total usage limit");
            }
            order.setCoupon(couponEntity);
            // Redemption — optimistic attempt, unique index protects duplicates.
            CouponRedemption redemption = CouponRedemption.create(couponEntity, order.getUser(), order);
            couponRedemptionRepository.save(redemption);
        }

        // 6. Order item snapshots.
        for (CartItem item : items) {
            var product = item.getProduct();
            BigDecimal unit = product.getSellingPrice();
            OrderItem orderItem = OrderItem.builder()
                    .product(product)
                    .productName(product.getName())
                    .productImageUrl(product.getImages().stream()
                            .filter(img -> Boolean.TRUE.equals(img.getIsPrimary()))
                            .map(img -> img.getImageUrl())
                            .findFirst().orElse(null))
                    .unitPrice(unit)
                    .quantity(item.getQuantity())
                    .lineTotal(unit.multiply(BigDecimal.valueOf(item.getQuantity())))
                    .build();
            order.addItem(orderItem);
        }

        // 7. Status-history seed (order itself was persisted above).
        appendHistory(order, null, OrderStatus.PENDING,
                "customer:" + order.getUser().getEmail(), "Order created via checkout");

        // 8a. COD: consume reserved stock and confirm immediately.
        if (order.getPaymentMethod() == PaymentMethod.COD) {
            for (CartItem item : items) {
                inventoryRepository.commitReservation(item.getProduct().getId(), item.getQuantity());
                var inv = inventoryRepository.findByProductId(item.getProduct().getId()).orElseThrow();
                inventoryTransactionRepository.save(com.shopsphere.inventory.entity.InventoryTransaction
                        .builder()
                        .inventory(inv)
                        .type(InventoryTransactionType.COMMIT)
                        .quantityDelta(-item.getQuantity())
                        .note("COD order " + order.getOrderNumber())
                        .orderId(order.getId())
                        .userId(userId)
                        .build());
            }
            OrderStatus previous = order.getStatus();
            order.setStatus(OrderStatus.CONFIRMED);
            orderRepository.save(order);
            appendHistory(order, previous, OrderStatus.CONFIRMED, "system", "COD order confirmed");
            cartItemRepository.deleteByCartId(cartIdOf(items));
        } else {
            // 8b. RAZORPAY: reservation held; payment orchestration below.
            paymentService.resolveRazorpayOrder(order);
            cartItemRepository.deleteByCartId(cartIdOf(items));
        }

        // 9. Notification for the customer.
        notificationSaver.orderCreated(order.getUser().getId(),
                "Order " + order.getOrderNumber() + " placed",
                "Total ₹" + order.getTotalAmount() + " — status: " + order.getStatus());

        String gwOrderId = paymentService.gatewayOrderIdFor(order.getId()).orElse(null);
        return new CheckoutResponse(order.getId(), order.getOrderNumber(),
                order.getStatus().name(), gwOrderId, paymentService.publishableKeyId());
    }

    // ------------------------------------------------------------ helpers
    private List<CartItem> cartItemsOf(Long userId) {
        return cartItemRepository.findAllByCartUserId(userId);
    }

    private Long cartIdOf(List<CartItem> items) {
        return items.get(0).getCart().getId();
    }

    private ShippingMethod requireShipping(Long id) {
        return SHIPPING_METHODS.stream().filter(m -> m.id().equals(id)).findFirst()
                .orElseThrow(() -> BusinessException.badRequest("SHIPPING_INVALID", "Unknown shipping method"));
    }

    private BigDecimal shippingFeeFor(ShippingMethod method, BigDecimal subtotal) {
        if (method.freeAbove() != null && subtotal.compareTo(method.freeAbove()) >= 0) {
            return BigDecimal.ZERO; // free shipping above threshold
        }
        return method.baseFee();
    }

    private String generateOrderNumber() {
        String year = Instant.now().atZone(java.time.ZoneId.of("Asia/Kolkata"))
                .format(DateTimeFormatter.ofPattern("yyyy"));
        return "SP-" + year + "-" + String.format("%06d", ORDER_RANDOM.nextInt(1_000_000));
    }

    private String addressSnapshot(Address address) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "contactName", safe(address.getContactName()),
                    "phone", safe(address.getPhone()),
                    "line1", safe(address.getLine1()),
                    "line2", safe(address.getLine2()),
                    "city", safe(address.getCity()),
                    "state", safe(address.getState()),
                    "pincode", safe(address.getPincode())));
        } catch (Exception e) {
            throw new IllegalStateException("Address snapshot failure", e);
        }
    }

    private String safe(String s) { return s == null ? "" : s; }

    private void appendHistory(Order order, OrderStatus from, OrderStatus to, String actor, String note) {
        var history = OrderStatusHistory.builder()
                .previousStatus(from).newStatus(to)
                .changedBy(actor).note(note)
                .build();
        history.attachTo(order);
        statusHistoryRepository.save(history);
    }
}
