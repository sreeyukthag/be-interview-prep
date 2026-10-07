package com.sreeyukthag.beinterviewprep.orders.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sreeyukthag.beinterviewprep.catalog.dto.request.ProductRequest;
import com.sreeyukthag.beinterviewprep.catalog.repository.ProductRepository;
import com.sreeyukthag.beinterviewprep.catalog.service.ProductService;
import com.sreeyukthag.beinterviewprep.catalog.stock.InsufficientStockException;
import com.sreeyukthag.beinterviewprep.common.exception.ResourceNotFoundException;
import com.sreeyukthag.beinterviewprep.orders.dto.request.OrderItemRequest;
import com.sreeyukthag.beinterviewprep.orders.dto.request.PlaceOrderRequest;
import com.sreeyukthag.beinterviewprep.orders.dto.response.OrderItemResponse;
import com.sreeyukthag.beinterviewprep.orders.dto.response.OrderResponse;
import com.sreeyukthag.beinterviewprep.orders.entity.OrderStatus;
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
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class OrderPlacementTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void fiftySimultaneousOrdersForTenUnitsSellExactlyTen() throws Exception {
        UUID productId = createProduct(10, 500);
        PlaceOrderRequest oneUnit = request(productId, 1);
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<String>> customers = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            customers.add(() -> {
                start.await();
                try {
                    orderService.place(UUID.randomUUID().toString(), oneUnit);
                    return "placed";
                } catch (InsufficientStockException ex) {
                    return "out-of-stock";
                }
            });
        }

        List<String> outcomes = runTogether(customers, start);

        assertThat(outcomes).filteredOn("placed"::equals).hasSize(10);
        assertThat(outcomes).filteredOn("out-of-stock"::equals).hasSize(40);
        assertThat(stockOf(productId)).isZero();
        assertThat(placedOrdersFor(productId)).isEqualTo(10);
    }

    @Test
    void placingAnOrderReservesStockAndSnapshotsPrices() {
        UUID lamp = createProduct(5, 1_500);
        UUID mug = createProduct(3, 400);
        PlaceOrderRequest request = new PlaceOrderRequest(
                List.of(new OrderItemRequest(lamp, 2), new OrderItemRequest(mug, 1), new OrderItemRequest(lamp, 1)));

        OrderResponse order = orderService.place(UUID.randomUUID().toString(), request);

        assertThat(order.status()).isEqualTo(OrderStatus.PLACED);
        assertThat(order.totalCents()).isEqualTo(3 * 1_500 + 400);
        assertThat(order.items())
                .extracting(OrderItemResponse::productId, OrderItemResponse::quantity)
                .containsExactlyInAnyOrder(tuple(lamp, 3), tuple(mug, 1));
        assertThat(stockOf(lamp)).isEqualTo(2);
        assertThat(stockOf(mug)).isEqualTo(2);
        assertThat(orderService.get(order.id()))
                .usingRecursiveComparison()
                .ignoringFields("createdAt", "updatedAt")
                .isEqualTo(order);
    }

    @Test
    void oneInsufficientItemRejectsTheWholeOrder() {
        UUID available = createProduct(5, 100);
        UUID scarce = createProduct(1, 100);
        PlaceOrderRequest request =
                new PlaceOrderRequest(List.of(new OrderItemRequest(available, 2), new OrderItemRequest(scarce, 2)));

        InsufficientStockException ex = assertThrows(
                InsufficientStockException.class,
                () -> orderService.place(UUID.randomUUID().toString(), request));

        assertThat(ex.getMessage()).contains(scarce.toString(), "requested 2", "available 1");
        assertThat(stockOf(available)).isEqualTo(5);
        assertThat(stockOf(scarce)).isEqualTo(1);
        assertThat(placedOrdersFor(available)).isZero();
    }

    @Test
    void unknownProductRejectsTheWholeOrder() {
        UUID available = createProduct(5, 100);
        PlaceOrderRequest request = new PlaceOrderRequest(
                List.of(new OrderItemRequest(available, 1), new OrderItemRequest(UUID.randomUUID(), 1)));

        assertThrows(
                ResourceNotFoundException.class,
                () -> orderService.place(UUID.randomUUID().toString(), request));

        assertThat(stockOf(available)).isEqualTo(5);
    }

    @Test
    void cachedProductShowsTheReducedStockAfterAnOrder() {
        UUID productId = createProduct(5, 100);
        productService.get(productId);

        orderService.place(UUID.randomUUID().toString(), request(productId, 2));

        assertThat(productService.get(productId).stock()).isEqualTo(3);
    }

    @Test
    void unknownOrderIsNotFound() {
        UUID unknown = UUID.randomUUID();

        assertThrows(ResourceNotFoundException.class, () -> orderService.get(unknown));
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

    private static PlaceOrderRequest request(UUID productId, int quantity) {
        return new PlaceOrderRequest(List.of(new OrderItemRequest(productId, quantity)));
    }

    private UUID createProduct(int stock, long priceCents) {
        return productService
                .create(new ProductRequest("Order Item", "home", priceCents, stock, 4.0))
                .id();
    }

    private int stockOf(UUID productId) {
        return productRepository.findById(productId).orElseThrow().getStock();
    }

    private int placedOrdersFor(UUID productId) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM orders o JOIN order_items i ON i.order_id = o.id
                WHERE i.product_id = ? AND o.status = 'PLACED'
                """, Integer.class, productId);
    }
}
