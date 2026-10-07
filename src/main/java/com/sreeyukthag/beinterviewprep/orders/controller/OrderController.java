package com.sreeyukthag.beinterviewprep.orders.controller;

import com.sreeyukthag.beinterviewprep.common.web.ApiResponse;
import com.sreeyukthag.beinterviewprep.orders.dto.request.PlaceOrderRequest;
import com.sreeyukthag.beinterviewprep.orders.dto.response.OrderResponse;
import com.sreeyukthag.beinterviewprep.orders.service.OrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> place(
            @RequestHeader(IDEMPOTENCY_KEY_HEADER)
                    @Pattern(
                            regexp = "[A-Za-z0-9_-]{1,64}",
                            message = "must be 1-64 letters, digits, '-' or '_' (a UUID works)")
                    String idempotencyKey,
            @Valid @RequestBody PlaceOrderRequest request) {
        OrderResponse order = orderService.place(idempotencyKey, request);
        return ResponseEntity.created(URI.create("/api/v1/orders/" + order.id()))
                .body(ApiResponse.ok(order));
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(orderService.get(id));
    }
}
