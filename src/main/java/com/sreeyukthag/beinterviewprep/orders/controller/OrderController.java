package com.sreeyukthag.beinterviewprep.orders.controller;

import com.sreeyukthag.beinterviewprep.common.web.ApiResponse;
import com.sreeyukthag.beinterviewprep.orders.dto.request.PlaceOrderRequest;
import com.sreeyukthag.beinterviewprep.orders.dto.response.OrderResponse;
import com.sreeyukthag.beinterviewprep.orders.dto.response.PlacedOrder;
import com.sreeyukthag.beinterviewprep.orders.service.OrderAccess;
import com.sreeyukthag.beinterviewprep.orders.service.OrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.annotation.CurrentSecurityContext;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
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
    public static final String REPLAYED_HEADER = "Idempotent-Replayed";
    private static final String ADMIN_AUTHORITY = "ROLE_ADMIN";

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> place(
            @RequestHeader(IDEMPOTENCY_KEY_HEADER)
                    @Pattern(
                            regexp = "[A-Za-z0-9_-]{1,64}",
                            message = "must be 1-64 letters, digits, '-' or '_' (a UUID works)")
                    String idempotencyKey,
            @Valid @RequestBody PlaceOrderRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        PlacedOrder placed = orderService.place(customerId(jwt), idempotencyKey, request);
        if (placed.replayed()) {
            return ResponseEntity.ok().header(REPLAYED_HEADER, "true").body(ApiResponse.ok(placed.order()));
        }
        return ResponseEntity.created(
                        URI.create("/api/v1/orders/" + placed.order().id()))
                .body(ApiResponse.ok(placed.order()));
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderResponse> get(
            @PathVariable UUID id,
            @CurrentSecurityContext(expression = "authentication") JwtAuthenticationToken caller) {
        return ApiResponse.ok(orderService.get(id, access(caller)));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<OrderResponse> cancel(
            @PathVariable UUID id,
            @CurrentSecurityContext(expression = "authentication") JwtAuthenticationToken caller) {
        return ApiResponse.ok(orderService.cancel(id, access(caller)));
    }

    private static OrderAccess access(JwtAuthenticationToken caller) {
        boolean admin =
                AuthorityUtils.authorityListToSet(caller.getAuthorities()).contains(ADMIN_AUTHORITY);
        return new OrderAccess(customerId(caller.getToken()), admin);
    }

    /** The token's subject is the user id; the customer is never taken from the body, path or a header. */
    private static UUID customerId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
