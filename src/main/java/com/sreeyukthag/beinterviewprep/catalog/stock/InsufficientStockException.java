package com.sreeyukthag.beinterviewprep.catalog.stock;

import com.sreeyukthag.beinterviewprep.common.exception.ConflictException;
import java.util.UUID;

public class InsufficientStockException extends ConflictException {

    public InsufficientStockException(UUID productId, String productName, int requested, int available) {
        super(
                "INSUFFICIENT_STOCK",
                "Insufficient stock for product '%s' (%s): requested %d, available %d"
                        .formatted(productName, productId, requested, available));
    }
}
