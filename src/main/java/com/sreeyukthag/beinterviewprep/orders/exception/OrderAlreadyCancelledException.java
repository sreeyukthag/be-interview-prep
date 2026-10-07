package com.sreeyukthag.beinterviewprep.orders.exception;

import com.sreeyukthag.beinterviewprep.common.exception.ConflictException;
import java.util.UUID;

public class OrderAlreadyCancelledException extends ConflictException {

    public OrderAlreadyCancelledException(UUID orderId) {
        super("ORDER_ALREADY_CANCELLED", "Order %s is already cancelled".formatted(orderId));
    }
}
