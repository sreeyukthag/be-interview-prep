package com.sreeyukthag.beinterviewprep.orders.dto.response;

/** {@code replayed} is true when the request was a retry and the order already existed. */
public record PlacedOrder(OrderResponse order, boolean replayed) {

    public static PlacedOrder created(OrderResponse order) {
        return new PlacedOrder(order, false);
    }

    public static PlacedOrder replayed(OrderResponse order) {
        return new PlacedOrder(order, true);
    }
}
