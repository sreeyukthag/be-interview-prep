package com.sreeyukthag.beinterviewprep.orders.mapper;

import com.sreeyukthag.beinterviewprep.orders.dto.response.OrderItemResponse;
import com.sreeyukthag.beinterviewprep.orders.dto.response.OrderResponse;
import com.sreeyukthag.beinterviewprep.orders.entity.Order;
import com.sreeyukthag.beinterviewprep.orders.entity.OrderItem;
import java.util.Comparator;

public final class OrderMapper {

    private OrderMapper() {}

    public static OrderResponse toResponse(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getStatus(),
                order.getTotalCents(),
                order.getItems().stream()
                        .sorted(Comparator.comparing(OrderItem::getProductId))
                        .map(OrderMapper::toResponse)
                        .toList(),
                order.getCreatedAt(),
                order.getUpdatedAt());
    }

    private static OrderItemResponse toResponse(OrderItem item) {
        return new OrderItemResponse(
                item.getProductId(), item.getQuantity(), item.getUnitPriceCents(), item.lineTotalCents());
    }
}
