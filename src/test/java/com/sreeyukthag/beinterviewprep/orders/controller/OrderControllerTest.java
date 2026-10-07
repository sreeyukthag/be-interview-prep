package com.sreeyukthag.beinterviewprep.orders.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sreeyukthag.beinterviewprep.catalog.stock.InsufficientStockException;
import com.sreeyukthag.beinterviewprep.common.exception.ResourceNotFoundException;
import com.sreeyukthag.beinterviewprep.orders.dto.response.OrderItemResponse;
import com.sreeyukthag.beinterviewprep.orders.dto.response.OrderResponse;
import com.sreeyukthag.beinterviewprep.orders.entity.OrderStatus;
import com.sreeyukthag.beinterviewprep.orders.service.OrderService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    private static final UUID ORDER_ID = UUID.fromString("6f1c2a52-1d1e-4c39-9d8f-0f3a1f0f6b10");
    private static final UUID PRODUCT_ID = UUID.fromString("8aa10fd1-20a5-5dea-88e0-ffd88a8d048b");
    private static final String KEY = "0b9f6a4e-5d0c-4f43-a8a8-1c2d3e4f5a6b";
    private static final String VALID_BODY = """
            {"items":[{"productId":"8aa10fd1-20a5-5dea-88e0-ffd88a8d048b","quantity":2}]}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    @Test
    void placeReturns201WithLocation() throws Exception {
        when(orderService.place(eq(KEY), any())).thenReturn(sampleOrder());

        mockMvc.perform(placeRequest(VALID_BODY).header("Idempotency-Key", KEY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/orders/" + ORDER_ID))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("PLACED"))
                .andExpect(jsonPath("$.data.totalCents").value(2_000));
    }

    @Test
    void missingIdempotencyKeyReturns400() throws Exception {
        mockMvc.perform(placeRequest(VALID_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("Idempotency-Key"))
                .andExpect(jsonPath("$.errors[0].message").value("is required"));

        verifyNoInteractions(orderService);
    }

    @Test
    void malformedIdempotencyKeyReturns400() throws Exception {
        String tooLong = "k".repeat(65);

        mockMvc.perform(placeRequest(VALID_BODY).header("Idempotency-Key", tooLong))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("Idempotency-Key"));
        mockMvc.perform(placeRequest(VALID_BODY).header("Idempotency-Key", "has spaces"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("Idempotency-Key"));

        verifyNoInteractions(orderService);
    }

    @Test
    void emptyItemsReturns400() throws Exception {
        mockMvc.perform(placeRequest("{\"items\":[]}").header("Idempotency-Key", KEY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("items"));

        verifyNoInteractions(orderService);
    }

    @Test
    void zeroQuantityReturns400ForThatItem() throws Exception {
        String body = """
                {"items":[{"productId":"8aa10fd1-20a5-5dea-88e0-ffd88a8d048b","quantity":0}]}
                """;

        mockMvc.perform(placeRequest(body).header("Idempotency-Key", KEY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("items[0].quantity"));

        verifyNoInteractions(orderService);
    }

    @Test
    void insufficientStockReturns409() throws Exception {
        when(orderService.place(eq(KEY), any()))
                .thenThrow(new InsufficientStockException(PRODUCT_ID, "Desk Lamp", 2, 1));

        mockMvc.perform(placeRequest(VALID_BODY).header("Idempotency-Key", KEY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("INSUFFICIENT_STOCK"))
                .andExpect(jsonPath("$.message")
                        .value("Insufficient stock for product 'Desk Lamp' (%s): requested 2, available 1"
                                .formatted(PRODUCT_ID)));
    }

    @Test
    void getReturnsTheOrder() throws Exception {
        when(orderService.get(ORDER_ID)).thenReturn(sampleOrder());

        mockMvc.perform(get("/api/v1/orders/{id}", ORDER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].productId").value(PRODUCT_ID.toString()))
                .andExpect(jsonPath("$.data.items[0].lineTotalCents").value(2_000));
    }

    @Test
    void unknownOrderReturns404() throws Exception {
        when(orderService.get(ORDER_ID)).thenThrow(new ResourceNotFoundException("Order", ORDER_ID));

        mockMvc.perform(get("/api/v1/orders/{id}", ORDER_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
    }

    private static MockHttpServletRequestBuilder placeRequest(String body) {
        return post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static OrderResponse sampleOrder() {
        Instant now = Instant.parse("2026-01-01T09:00:00Z");
        return new OrderResponse(
                ORDER_ID,
                OrderStatus.PLACED,
                2_000,
                List.of(new OrderItemResponse(PRODUCT_ID, 2, 1_000, 2_000)),
                now,
                now);
    }
}
