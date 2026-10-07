package com.sreeyukthag.beinterviewprep.orders.dto.response;

import com.sreeyukthag.beinterviewprep.orders.entity.OrderStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        OrderStatus status,
        long totalCents,
        List<OrderItemResponse> items,
        Instant createdAt,
        Instant updatedAt) {}
