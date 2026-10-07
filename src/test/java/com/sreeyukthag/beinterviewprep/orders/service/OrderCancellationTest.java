package com.sreeyukthag.beinterviewprep.orders.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sreeyukthag.beinterviewprep.catalog.dto.request.ProductRequest;
import com.sreeyukthag.beinterviewprep.catalog.repository.ProductRepository;
import com.sreeyukthag.beinterviewprep.catalog.service.ProductService;
import com.sreeyukthag.beinterviewprep.common.exception.ResourceNotFoundException;
import com.sreeyukthag.beinterviewprep.orders.dto.request.OrderItemRequest;
import com.sreeyukthag.beinterviewprep.orders.dto.request.PlaceOrderRequest;
import com.sreeyukthag.beinterviewprep.orders.dto.response.OrderResponse;
import com.sreeyukthag.beinterviewprep.orders.dto.response.PlacedOrder;
import com.sreeyukthag.beinterviewprep.orders.entity.OrderStatus;
import com.sreeyukthag.beinterviewprep.orders.exception.OrderAlreadyCancelledException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class OrderCancellationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Test
    void cancellingReturnsEveryItemsStock() {
        UUID lamp = createProduct(5);
        UUID mug = createProduct(4);
        OrderResponse order = place(
                        new PlaceOrderRequest(List.of(new OrderItemRequest(lamp, 3), new OrderItemRequest(mug, 1))))
                .order();

        OrderResponse cancelled = orderService.cancel(order.id());

        assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(orderService.get(order.id()).status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(stockOf(lamp)).isEqualTo(5);
        assertThat(stockOf(mug)).isEqualTo(4);
    }

    @Test
    void cancellingTwiceReleasesStockOnce() {
        UUID productId = createProduct(5);
        UUID orderId = place(request(productId, 2)).order().id();
        orderService.cancel(orderId);

        assertThrows(OrderAlreadyCancelledException.class, () -> orderService.cancel(orderId));

        assertThat(stockOf(productId)).isEqualTo(5);
    }

    @Test
    void simultaneousCancelsReleaseStockOnce() throws Exception {
        UUID productId = createProduct(5);
        UUID orderId = place(request(productId, 2)).order().id();
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<String>> cancels = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            cancels.add(() -> {
                start.await();
                try {
                    orderService.cancel(orderId);
                    return "cancelled";
                } catch (OrderAlreadyCancelledException ex) {
                    return "already-cancelled";
                }
            });
        }

        List<String> outcomes = runTogether(cancels, start);

        assertThat(outcomes).filteredOn("cancelled"::equals).hasSize(1);
        assertThat(outcomes).filteredOn("already-cancelled"::equals).hasSize(9);
        assertThat(stockOf(productId)).isEqualTo(5);
    }

    @Test
    void cachedProductShowsTheReturnedStockAfterCancelling() {
        UUID productId = createProduct(5);
        UUID orderId = place(request(productId, 2)).order().id();
        productService.get(productId);

        orderService.cancel(orderId);

        assertThat(productService.get(productId).stock()).isEqualTo(5);
    }

    @Test
    void retryingACancelledOrdersRequestDoesNotReserveAgain() {
        UUID productId = createProduct(5);
        String key = UUID.randomUUID().toString();
        UUID orderId = orderService.place(key, request(productId, 2)).order().id();
        orderService.cancel(orderId);

        PlacedOrder retry = orderService.place(key, request(productId, 2));

        assertThat(retry.replayed()).isTrue();
        assertThat(retry.order().status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(stockOf(productId)).isEqualTo(5);
    }

    @Test
    void cancellingAnUnknownOrderIsNotFound() {
        UUID unknown = UUID.randomUUID();

        assertThrows(ResourceNotFoundException.class, () -> orderService.cancel(unknown));
    }

    private static <T> List<T> runTogether(List<Callable<T>> tasks, CountDownLatch start) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        try {
            List<Future<T>> futures = new ArrayList<>();
            tasks.forEach(task -> futures.add(pool.submit(task)));
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private PlacedOrder place(PlaceOrderRequest request) {
        return orderService.place(UUID.randomUUID().toString(), request);
    }

    private static PlaceOrderRequest request(UUID productId, int quantity) {
        return new PlaceOrderRequest(List.of(new OrderItemRequest(productId, quantity)));
    }

    private UUID createProduct(int stock) {
        return productService
                .create(new ProductRequest("Cancel Item", "home", 1_000L, stock, 4.0))
                .id();
    }

    private int stockOf(UUID productId) {
        return productRepository.findById(productId).orElseThrow().getStock();
    }
}
