package com.sreeyukthag.beinterviewprep.orders.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import com.sreeyukthag.beinterviewprep.orders.dto.response.PlacedOrder;
import com.sreeyukthag.beinterviewprep.orders.entity.OrderStatus;
import com.sreeyukthag.beinterviewprep.orders.exception.IdempotencyKeyReusedException;
import com.sreeyukthag.beinterviewprep.orders.exception.OrderAlreadyCancelledException;
import com.sreeyukthag.beinterviewprep.orders.service.OrderAccess;
import com.sreeyukthag.beinterviewprep.orders.service.OrderService;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(OrderController.class)
@AutoConfigureMockMvc(addFilters = false)
class OrderControllerTest {

    private static final UUID CUSTOMER_ID = UUID.fromString("3b0c8f9e-6d2a-4f1e-9a57-2c4e8d1b7f60");
    private static final OrderAccess AS_CUSTOMER = new OrderAccess(CUSTOMER_ID, false);
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
        when(orderService.place(eq(CUSTOMER_ID), eq(KEY), any())).thenReturn(PlacedOrder.created(sampleOrder()));

        mockMvc.perform(placeRequest(VALID_BODY).header("Idempotency-Key", KEY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/orders/" + ORDER_ID))
                .andExpect(header().doesNotExist("Idempotent-Replayed"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("PLACED"))
                .andExpect(jsonPath("$.data.totalCents").value(2_000));
    }

    @Test
    void customerComesFromTheTokenNotTheBody() throws Exception {
        UUID someoneElse = UUID.randomUUID();
        String body = """
                {"customerId":"%s","items":[{"productId":"8aa10fd1-20a5-5dea-88e0-ffd88a8d048b","quantity":2}]}
                """.formatted(someoneElse);
        when(orderService.place(eq(CUSTOMER_ID), eq(KEY), any())).thenReturn(PlacedOrder.created(sampleOrder()));

        mockMvc.perform(placeRequest(body).header("Idempotency-Key", KEY)).andExpect(status().isCreated());

        verify(orderService).place(eq(CUSTOMER_ID), eq(KEY), any());
        verify(orderService, never()).place(eq(someoneElse), any(), any());
    }

    @Test
    void replayedOrderReturns200WithTheExistingOrder() throws Exception {
        when(orderService.place(eq(CUSTOMER_ID), eq(KEY), any())).thenReturn(PlacedOrder.replayed(sampleOrder()));

        mockMvc.perform(placeRequest(VALID_BODY).header("Idempotency-Key", KEY))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$.data.id").value(ORDER_ID.toString()));
    }

    @Test
    void reusedKeyWithADifferentBodyReturns422() throws Exception {
        when(orderService.place(eq(CUSTOMER_ID), eq(KEY), any())).thenThrow(new IdempotencyKeyReusedException(KEY));

        mockMvc.perform(placeRequest(VALID_BODY).header("Idempotency-Key", KEY))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("IDEMPOTENCY_KEY_REUSED"));
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
        when(orderService.place(eq(CUSTOMER_ID), eq(KEY), any()))
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
        when(orderService.get(ORDER_ID, AS_CUSTOMER)).thenReturn(sampleOrder());

        mockMvc.perform(get("/api/v1/orders/{id}", ORDER_ID).with(authenticatedAs(CUSTOMER_ID, "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].productId").value(PRODUCT_ID.toString()))
                .andExpect(jsonPath("$.data.items[0].lineTotalCents").value(2_000));
    }

    @Test
    void unknownOrderReturns404() throws Exception {
        when(orderService.get(ORDER_ID, AS_CUSTOMER)).thenThrow(new ResourceNotFoundException("Order", ORDER_ID));

        mockMvc.perform(get("/api/v1/orders/{id}", ORDER_ID).with(authenticatedAs(CUSTOMER_ID, "USER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
    }

    @Test
    void cancelReturnsTheCancelledOrder() throws Exception {
        OrderResponse placed = sampleOrder();
        OrderResponse cancelled = new OrderResponse(
                placed.id(),
                OrderStatus.CANCELLED,
                placed.totalCents(),
                placed.items(),
                placed.createdAt(),
                placed.updatedAt());
        when(orderService.cancel(ORDER_ID, AS_CUSTOMER)).thenReturn(cancelled);

        mockMvc.perform(post("/api/v1/orders/{id}/cancel", ORDER_ID).with(authenticatedAs(CUSTOMER_ID, "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    void cancellingTwiceReturns409() throws Exception {
        when(orderService.cancel(ORDER_ID, AS_CUSTOMER)).thenThrow(new OrderAlreadyCancelledException(ORDER_ID));

        mockMvc.perform(post("/api/v1/orders/{id}/cancel", ORDER_ID).with(authenticatedAs(CUSTOMER_ID, "USER")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ORDER_ALREADY_CANCELLED"));
    }

    @Test
    void cancellingAnUnknownOrderReturns404() throws Exception {
        when(orderService.cancel(ORDER_ID, AS_CUSTOMER)).thenThrow(new ResourceNotFoundException("Order", ORDER_ID));

        mockMvc.perform(post("/api/v1/orders/{id}/cancel", ORDER_ID).with(authenticatedAs(CUSTOMER_ID, "USER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
    }

    @Test
    void anAdminTokenReadsAndCancelsWithAdminAccess() throws Exception {
        UUID adminId = UUID.randomUUID();
        OrderAccess asAdmin = new OrderAccess(adminId, true);
        when(orderService.get(ORDER_ID, asAdmin)).thenReturn(sampleOrder());
        when(orderService.cancel(ORDER_ID, asAdmin)).thenReturn(sampleOrder());

        mockMvc.perform(get("/api/v1/orders/{id}", ORDER_ID).with(authenticatedAs(adminId, "ADMIN")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/orders/{id}/cancel", ORDER_ID).with(authenticatedAs(adminId, "ADMIN")))
                .andExpect(status().isOk());

        verify(orderService).get(ORDER_ID, asAdmin);
        verify(orderService).cancel(ORDER_ID, asAdmin);
    }

    private static MockHttpServletRequestBuilder placeRequest(String body) {
        return post("/api/v1/orders")
                .with(authenticatedAs(CUSTOMER_ID, "USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    /**
     * The slice runs without the security filters, so {@code jwt()} would never reach the controller; putting the
     * token straight into the test security context is what {@code @AuthenticationPrincipal} reads.
     */
    private static RequestPostProcessor authenticatedAs(UUID userId, String... roles) {
        return request -> {
            Jwt jwt = Jwt.withTokenValue("test-token")
                    .header("alg", "HS256")
                    .subject(userId.toString())
                    .build();
            String[] authorities =
                    Arrays.stream(roles).map(role -> "ROLE_" + role).toArray(String[]::new);
            TestSecurityContextHolder.setAuthentication(
                    new JwtAuthenticationToken(jwt, AuthorityUtils.createAuthorityList(authorities)));
            return request;
        };
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
