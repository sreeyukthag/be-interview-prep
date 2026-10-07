package com.sreeyukthag.beinterviewprep.orders.service;

import com.sreeyukthag.beinterviewprep.catalog.stock.ReservedItem;
import com.sreeyukthag.beinterviewprep.catalog.stock.StockService;
import com.sreeyukthag.beinterviewprep.common.exception.ResourceNotFoundException;
import com.sreeyukthag.beinterviewprep.orders.dto.request.PlaceOrderRequest;
import com.sreeyukthag.beinterviewprep.orders.dto.response.OrderResponse;
import com.sreeyukthag.beinterviewprep.orders.dto.response.PlacedOrder;
import com.sreeyukthag.beinterviewprep.orders.entity.Order;
import com.sreeyukthag.beinterviewprep.orders.entity.OrderItem;
import com.sreeyukthag.beinterviewprep.orders.entity.OrderStatus;
import com.sreeyukthag.beinterviewprep.orders.exception.IdempotencyKeyReusedException;
import com.sreeyukthag.beinterviewprep.orders.exception.OrderAlreadyCancelledException;
import com.sreeyukthag.beinterviewprep.orders.mapper.OrderMapper;
import com.sreeyukthag.beinterviewprep.orders.repository.OrderRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final StockService stockService;
    private final TransactionTemplate transactionTemplate;

    /**
     * Deliberately not {@code @Transactional}: the duplicate-key failure must be caught after the losing transaction
     * has rolled back (PostgreSQL refuses further statements in a failed transaction), then answered with the
     * winner's order. Even {@code Propagation.NEVER} would hold the lookup's connection while the placement borrows
     * a second one, which exhausts the pool under load.
     */
    public PlacedOrder place(UUID customerId, String idempotencyKey, PlaceOrderRequest request) {
        List<OrderLine> lines = OrderLine.normalise(request.items());
        String fingerprint = OrderLine.fingerprint(lines);
        Optional<Order> existing =
                orderRepository.findWithItemsByCustomerIdAndIdempotencyKey(customerId, idempotencyKey);
        if (existing.isPresent()) {
            return replay(existing.get(), fingerprint);
        }
        try {
            return PlacedOrder.created(
                    transactionTemplate.execute(status -> placeNew(customerId, idempotencyKey, fingerprint, lines)));
        } catch (DataIntegrityViolationException ex) {
            Order winner = orderRepository
                    .findWithItemsByCustomerIdAndIdempotencyKey(customerId, idempotencyKey)
                    .orElseThrow(() -> ex);
            log.info("Concurrent retry with idempotency key {} resolved to order {}", idempotencyKey, winner.getId());
            return replay(winner, fingerprint);
        }
    }

    @Transactional(readOnly = true)
    public OrderResponse get(UUID id) {
        return OrderMapper.toResponse(find(id));
    }

    /** The status change and every stock release commit together, or not at all. */
    @Transactional
    public OrderResponse cancel(UUID id) {
        if (orderRepository.transition(id, OrderStatus.PLACED, OrderStatus.CANCELLED, Instant.now()) == 0) {
            throw orderRepository.existsById(id)
                    ? new OrderAlreadyCancelledException(id)
                    : new ResourceNotFoundException("Order", id);
        }
        Order order = find(id);
        order.getItems().stream()
                .sorted(Comparator.comparing(OrderItem::getProductId))
                .forEach(item -> stockService.release(item.getProductId(), item.getQuantity()));
        return OrderMapper.toResponse(order);
    }

    private Order find(UUID id) {
        return orderRepository.findWithItemsById(id).orElseThrow(() -> new ResourceNotFoundException("Order", id));
    }

    private OrderResponse placeNew(UUID customerId, String idempotencyKey, String fingerprint, List<OrderLine> lines) {
        Order order = Order.placed(customerId, idempotencyKey, fingerprint);
        for (OrderLine line : lines) {
            ReservedItem reserved = stockService.reserve(line.productId(), line.quantity());
            order.addItem(reserved.productId(), reserved.quantity(), reserved.unitPriceCents());
        }
        return OrderMapper.toResponse(orderRepository.saveAndFlush(order));
    }

    private static PlacedOrder replay(Order order, String fingerprint) {
        if (!order.getRequestHash().equals(fingerprint)) {
            throw new IdempotencyKeyReusedException(order.getIdempotencyKey());
        }
        return PlacedOrder.replayed(OrderMapper.toResponse(order));
    }
}
