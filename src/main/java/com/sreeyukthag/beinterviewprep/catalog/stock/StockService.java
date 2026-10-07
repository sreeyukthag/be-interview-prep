package com.sreeyukthag.beinterviewprep.catalog.stock;

import java.util.UUID;

/**
 * Both methods join the caller's transaction, so a caller reserving several products gets all-or-nothing:
 * a failure rolls back every reservation made earlier in the same transaction.
 */
public interface StockService {

    /**
     * Atomically takes {@code quantity} units off the product's stock.
     *
     * @throws InsufficientStockException when fewer than {@code quantity} units are left
     * @throws com.sreeyukthag.beinterviewprep.common.exception.ResourceNotFoundException for an unknown product
     */
    ReservedItem reserve(UUID productId, int quantity);

    /** Puts {@code quantity} units back on the product's stock. */
    void release(UUID productId, int quantity);
}
