package com.sreeyukthag.beinterviewprep.orders.repository;

import com.sreeyukthag.beinterviewprep.orders.entity.Order;
import com.sreeyukthag.beinterviewprep.orders.entity.OrderStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    @EntityGraph(attributePaths = "items")
    Optional<Order> findWithItemsById(UUID id);

    @EntityGraph(attributePaths = "items")
    Optional<Order> findWithItemsByCustomerIdAndIdempotencyKey(UUID customerId, String idempotencyKey);

    /**
     * Moves the order from {@code from} to {@code to} only if it is still in {@code from}. Of two concurrent cancels
     * the second waits on the row lock, then sees the new status and updates nothing.
     */
    @Modifying
    @Query("""
            UPDATE Order o SET o.status = :to, o.version = o.version + 1, o.updatedAt = :now
            WHERE o.id = :id AND o.status = :from
            """)
    int transition(
            @Param("id") UUID id,
            @Param("from") OrderStatus from,
            @Param("to") OrderStatus to,
            @Param("now") Instant now);
}
