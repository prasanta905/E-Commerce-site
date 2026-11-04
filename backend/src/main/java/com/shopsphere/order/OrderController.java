package com.shopsphere.order;

import java.util.List;

import com.shopsphere.common.ApiResponse;
import com.shopsphere.common.PageResponse;
import com.shopsphere.security.CurrentUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Orders", description = "Customer order history, detail, cancel and returns")
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;
    private final CurrentUser currentUser;

    public OrderController(OrderService orderService, CurrentUser currentUser) {
        this.orderService = orderService;
        this.currentUser = currentUser;
    }

    public record CancelRequest(@NotBlank @Size(max = 500) String reason) {}

    public record ReturnRequest(
            @NotBlank @Size(max = 1000) String reason,
            List<Long> itemIds) {}

    public record CancelledResponse(Long orderId, String orderNumber, String status, String message) {}

    public record ReturnCreatedResponse(Long orderId, String status, String message) {}

    @Operation(summary = "Paged list of the caller's orders (newest first)")
    @GetMapping
    public ResponseEntity<ApiResponse<PagedOrders>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        org.springframework.data.domain.Page<OrderSummaryDto> result =
                orderService.listForUser(currentUser.requireUserId(), page, size);
        return ResponseEntity.ok(ApiResponse.ok(
                new PagedOrders(result.getContent(), PageResponse.from(result))));
    }

    public record PagedOrders(List<OrderSummaryDto> items, PageResponse meta) {}

    @Operation(summary = "Full order detail — items, status history, tracking, addresses")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderDetailDto>> detail(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(
                orderService.detailForUser(id, currentUser.requireUserId())));
    }

    @Operation(summary = "Cancel an order (allowed while PENDING/CONFIRMED; stock released/restored)")
    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<CancelledResponse>> cancel(
            @PathVariable Long id, @Valid @RequestBody CancelRequest request) {
        orderService.cancel(id, currentUser.requireUserId(), request.reason());
        OrderDetailDto updated = orderService.detailForUser(id, currentUser.requireUserId());
        return ResponseEntity.ok(ApiResponse.ok(new CancelledResponse(
                id, updated.orderNumber(), updated.status(),
                "Order cancelled. Reservation released and stock restored.")));
    }

    @Operation(summary = "Request a return (only DELIVERED orders; one active return per order)")
    @PostMapping("/{id}/return-request")
    public ResponseEntity<ApiResponse<ReturnCreatedResponse>> requestReturn(
            @PathVariable Long id, @Valid @RequestBody ReturnRequest request) {
        orderService.requestReturn(id, currentUser.requireUserId(), request.reason());
        OrderDetailDto updated = orderService.detailForUser(id, currentUser.requireUserId());
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
                .body(ApiResponse.ok(new ReturnCreatedResponse(
                        id, updated.status(), "Return request submitted; awaiting admin review.")));
    }
}
