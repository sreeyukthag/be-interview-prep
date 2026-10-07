package com.sreeyukthag.beinterviewprep.orders.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.sreeyukthag.beinterviewprep.catalog.dto.request.ProductRequest;
import com.sreeyukthag.beinterviewprep.catalog.repository.ProductRepository;
import com.sreeyukthag.beinterviewprep.catalog.service.ProductService;
import com.sreeyukthag.beinterviewprep.orders.dto.request.OrderItemRequest;
import com.sreeyukthag.beinterviewprep.orders.dto.request.PlaceOrderRequest;
import com.sreeyukthag.beinterviewprep.orders.dto.response.PlacedOrder;
import com.sreeyukthag.beinterviewprep.orders.exception.IdempotencyKeyReusedException;
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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class OrderIdempotencyTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void retryingTheSameRequestOverHttpCreatesOneOrder() throws Exception {
        UUID productId = createProduct(5);
        String key = UUID.randomUUID().toString();
        String body = """
                {"items":[{"productId":"%s","quantity":2}]}
                """.formatted(productId);

        String first = mockMvc.perform(post("/api/v1/orders")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String firstId = JsonPath.read(first, "$.data.id");
        mockMvc.perform(post("/api/v1/orders")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(jsonPath("$.data.id").value(firstId));

        assertThat(ordersWithKey(key)).isEqualTo(1);
        assertThat(stockOf(productId)).isEqualTo(3);
    }

    @Test
    void retryListingTheSameItemsInAnotherOrderIsStillAReplay() {
        UUID lamp = createProduct(5);
        UUID mug = createProduct(5);
        String key = UUID.randomUUID().toString();
        PlaceOrderRequest original =
                new PlaceOrderRequest(List.of(new OrderItemRequest(lamp, 1), new OrderItemRequest(mug, 2)));
        PlaceOrderRequest reordered =
                new PlaceOrderRequest(List.of(new OrderItemRequest(mug, 2), new OrderItemRequest(lamp, 1)));

        PlacedOrder first = orderService.place(key, original);
        PlacedOrder retry = orderService.place(key, reordered);

        assertThat(retry.replayed()).isTrue();
        assertThat(retry.order().id()).isEqualTo(first.order().id());
        assertThat(stockOf(lamp)).isEqualTo(4);
        assertThat(stockOf(mug)).isEqualTo(3);
    }

    @Test
    void reusingAKeyForDifferentItemsIsRejectedWithoutTouchingStock() {
        UUID productId = createProduct(5);
        String key = UUID.randomUUID().toString();
        orderService.place(key, request(productId, 1));

        assertThrows(IdempotencyKeyReusedException.class, () -> orderService.place(key, request(productId, 2)));

        assertThat(stockOf(productId)).isEqualTo(4);
        assertThat(ordersWithKey(key)).isEqualTo(1);
    }

    @Test
    void simultaneousRetriesWithOneKeyCreateOneOrderAndReserveOnce() throws Exception {
        UUID productId = createProduct(20);
        String key = UUID.randomUUID().toString();
        PlaceOrderRequest request = request(productId, 2);
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<PlacedOrder>> retries = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            retries.add(() -> {
                start.await();
                return orderService.place(key, request);
            });
        }

        List<PlacedOrder> results = runTogether(retries, start);

        assertThat(results).filteredOn(placed -> !placed.replayed()).hasSize(1);
        assertThat(results)
                .extracting(placed -> placed.order().id())
                .containsOnly(results.get(0).order().id());
        assertThat(ordersWithKey(key)).isEqualTo(1);
        assertThat(stockOf(productId)).isEqualTo(18);
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

    private UUID createProduct(int stock) {
        return productService
                .create(new ProductRequest("Retry Item", "home", 1_000L, stock, 4.0))
                .id();
    }

    private int stockOf(UUID productId) {
        return productRepository.findById(productId).orElseThrow().getStock();
    }

    private int ordersWithKey(String key) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders WHERE idempotency_key = ?", Integer.class, key);
    }
}
