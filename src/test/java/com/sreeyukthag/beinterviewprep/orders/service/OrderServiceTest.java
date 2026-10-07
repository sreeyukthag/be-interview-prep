package com.sreeyukthag.beinterviewprep.orders.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sreeyukthag.beinterviewprep.catalog.stock.ReservedItem;
import com.sreeyukthag.beinterviewprep.catalog.stock.StockService;
import com.sreeyukthag.beinterviewprep.common.exception.ResourceNotFoundException;
import com.sreeyukthag.beinterviewprep.orders.dto.request.OrderItemRequest;
import com.sreeyukthag.beinterviewprep.orders.dto.request.PlaceOrderRequest;
import com.sreeyukthag.beinterviewprep.orders.dto.response.PlacedOrder;
import com.sreeyukthag.beinterviewprep.orders.entity.Order;
import com.sreeyukthag.beinterviewprep.orders.entity.OrderStatus;
import com.sreeyukthag.beinterviewprep.orders.exception.IdempotencyKeyReusedException;
import com.sreeyukthag.beinterviewprep.orders.exception.OrderAlreadyCancelledException;
import com.sreeyukthag.beinterviewprep.orders.repository.OrderRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final UUID CUSTOMER_ID = UUID.fromString("3b0c8f9e-6d2a-4f1e-9a57-2c4e8d1b7f60");
    private static final String KEY = "retry-key";
    private static final UUID PRODUCT_ID = UUID.fromString("8aa10fd1-20a5-5dea-88e0-ffd88a8d048b");
    private static final PlaceOrderRequest REQUEST =
            new PlaceOrderRequest(List.of(new OrderItemRequest(PRODUCT_ID, 2)));

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private StockService stockService;

    @Mock
    private TransactionTemplate transactionTemplate;

    @InjectMocks
    private OrderService orderService;

    @BeforeEach
    void runTransactionCallbacksInline() {
        lenient()
                .when(transactionTemplate.execute(any()))
                .thenAnswer(invocation ->
                        invocation.<TransactionCallback<?>>getArgument(0).doInTransaction(null));
    }

    @Test
    void retryWithTheSameItemsReplaysTheExistingOrderWithoutTouchingStock() {
        Order existing = existingOrder(REQUEST);
        when(orderRepository.findWithItemsByCustomerIdAndIdempotencyKey(CUSTOMER_ID, KEY))
                .thenReturn(Optional.of(existing));

        PlacedOrder placed = orderService.place(CUSTOMER_ID, KEY, REQUEST);

        assertThat(placed.replayed()).isTrue();
        assertThat(placed.order().totalCents()).isEqualTo(2_000);
        verifyNoInteractions(stockService, transactionTemplate);
    }

    @Test
    void reusingAKeyForDifferentItemsIsRejected() {
        Order existing = existingOrder(REQUEST);
        when(orderRepository.findWithItemsByCustomerIdAndIdempotencyKey(CUSTOMER_ID, KEY))
                .thenReturn(Optional.of(existing));
        PlaceOrderRequest different = new PlaceOrderRequest(List.of(new OrderItemRequest(PRODUCT_ID, 3)));

        assertThrows(IdempotencyKeyReusedException.class, () -> orderService.place(CUSTOMER_ID, KEY, different));

        verifyNoInteractions(stockService);
    }

    @Test
    void newKeyReservesStockAndCreatesTheOrder() {
        when(orderRepository.findWithItemsByCustomerIdAndIdempotencyKey(CUSTOMER_ID, KEY))
                .thenReturn(Optional.empty());
        when(stockService.reserve(PRODUCT_ID, 2)).thenReturn(new ReservedItem(PRODUCT_ID, 2, 1_000));
        when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PlacedOrder placed = orderService.place(CUSTOMER_ID, KEY, REQUEST);

        assertThat(placed.replayed()).isFalse();
        assertThat(placed.order().totalCents()).isEqualTo(2_000);
        ArgumentCaptor<Order> saved = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getCustomerId()).isEqualTo(CUSTOMER_ID);
    }

    @Test
    void losingAConcurrentRetryReturnsTheWinnersOrder() {
        Order winner = existingOrder(REQUEST);
        when(orderRepository.findWithItemsByCustomerIdAndIdempotencyKey(CUSTOMER_ID, KEY))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winner));
        when(stockService.reserve(PRODUCT_ID, 2)).thenReturn(new ReservedItem(PRODUCT_ID, 2, 1_000));
        when(orderRepository.saveAndFlush(any(Order.class)))
                .thenThrow(new DataIntegrityViolationException("uk_orders_customer_idempotency_key"));

        PlacedOrder placed = orderService.place(CUSTOMER_ID, KEY, REQUEST);

        assertThat(placed.replayed()).isTrue();
    }

    @Test
    void anIntegrityViolationWithNoWinnerIsRethrown() {
        when(orderRepository.findWithItemsByCustomerIdAndIdempotencyKey(CUSTOMER_ID, KEY))
                .thenReturn(Optional.empty());
        when(stockService.reserve(PRODUCT_ID, 2)).thenReturn(new ReservedItem(PRODUCT_ID, 2, 1_000));
        when(orderRepository.saveAndFlush(any(Order.class)))
                .thenThrow(new DataIntegrityViolationException("ck_order_items_quantity_positive"));

        assertThrows(DataIntegrityViolationException.class, () -> orderService.place(CUSTOMER_ID, KEY, REQUEST));
    }

    @Test
    void cancellingAnOrderTheCallerCannotSeeIsNotFoundAndReleasesNothing() {
        UUID orderId = UUID.randomUUID();
        OrderAccess access = new OrderAccess(CUSTOMER_ID, false);
        when(orderRepository.transition(
                        eq(orderId),
                        eq(CUSTOMER_ID),
                        eq(false),
                        eq(OrderStatus.PLACED),
                        eq(OrderStatus.CANCELLED),
                        any()))
                .thenReturn(0);
        when(orderRepository.existsVisible(orderId, CUSTOMER_ID, false)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> orderService.cancel(orderId, access));

        verifyNoInteractions(stockService);
    }

    @Test
    void cancellingAVisibleOrderThatIsNoLongerPlacedConflicts() {
        UUID orderId = UUID.randomUUID();
        OrderAccess access = new OrderAccess(CUSTOMER_ID, false);
        when(orderRepository.transition(
                        eq(orderId),
                        eq(CUSTOMER_ID),
                        eq(false),
                        eq(OrderStatus.PLACED),
                        eq(OrderStatus.CANCELLED),
                        any()))
                .thenReturn(0);
        when(orderRepository.existsVisible(orderId, CUSTOMER_ID, false)).thenReturn(true);

        assertThrows(OrderAlreadyCancelledException.class, () -> orderService.cancel(orderId, access));

        verifyNoInteractions(stockService);
    }

    @Test
    void anOrderTheCallerCannotSeeIsNotFound() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findVisible(orderId, CUSTOMER_ID, false)).thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class, () -> orderService.get(orderId, new OrderAccess(CUSTOMER_ID, false)));
    }

    private static Order existingOrder(PlaceOrderRequest request) {
        Order order = Order.placed(CUSTOMER_ID, KEY, OrderLine.fingerprint(OrderLine.normalise(request.items())));
        request.items().forEach(item -> order.addItem(item.productId(), item.quantity(), 1_000));
        return order;
    }
}
