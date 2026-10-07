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

    /**
     * The ownership check is part of the WHERE clause, so another customer's order is simply not found. {@code admin}
     * is bound as a flag that short-circuits the owner check, which keeps one query for both kinds of caller.
     */
    @EntityGraph(attributePaths = "items")
    @Query("""
            SELECT o FROM Order o
            WHERE o.id = :id AND (:admin = TRUE OR o.customerId = :customerId)
            """)
    Optional<Order> findVisible(
            @Param("id") UUID id, @Param("customerId") UUID customerId, @Param("admin") boolean admin);

    @Query("""
            SELECT COUNT(o) > 0 FROM Order o
            WHERE o.id = :id AND (:admin = TRUE OR o.customerId = :customerId)
            """)
    boolean existsVisible(@Param("id") UUID id, @Param("customerId") UUID customerId, @Param("admin") boolean admin);

    @EntityGraph(attributePaths = "items")
    Optional<Order> findWithItemsByCustomerIdAndIdempotencyKey(UUID customerId, String idempotencyKey);

    /**
     * Moves a visible order from {@code from} to {@code to} only if it is still in {@code from}. Of two concurrent
     * cancels the second waits on the row lock, then sees the new status and updates nothing.
     */
    @Modifying
    @Query("""
            UPDATE Order o SET o.status = :to, o.version = o.version + 1, o.updatedAt = :now
            WHERE o.id = :id AND o.status = :from AND (:admin = TRUE OR o.customerId = :customerId)
            """)
    int transition(
            @Param("id") UUID id,
            @Param("customerId") UUID customerId,
            @Param("admin") boolean admin,
            @Param("from") OrderStatus from,
            @Param("to") OrderStatus to,
            @Param("now") Instant now);
}
