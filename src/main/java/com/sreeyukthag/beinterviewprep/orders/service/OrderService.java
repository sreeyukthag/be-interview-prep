package com.sreeyukthag.beinterviewprep.orders.service;

import com.sreeyukthag.beinterviewprep.catalog.stock.ReservedItem;
import com.sreeyukthag.beinterviewprep.catalog.stock.StockService;
import com.sreeyukthag.beinterviewprep.common.exception.ResourceNotFoundException;
import com.sreeyukthag.beinterviewprep.orders.dto.request.PlaceOrderRequest;
import com.sreeyukthag.beinterviewprep.orders.dto.response.OrderResponse;
import com.sreeyukthag.beinterviewprep.orders.entity.Order;
import com.sreeyukthag.beinterviewprep.orders.mapper.OrderMapper;
import com.sreeyukthag.beinterviewprep.orders.repository.OrderRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

    private final OrderRepository orderRepository;
    private final StockService stockService;

    @Transactional
    public OrderResponse place(String idempotencyKey, PlaceOrderRequest request) {
        List<OrderLine> lines = OrderLine.normalise(request.items());
        Order order = Order.placed(idempotencyKey, OrderLine.fingerprint(lines));
        for (OrderLine line : lines) {
            ReservedItem reserved = stockService.reserve(line.productId(), line.quantity());
            order.addItem(reserved.productId(), reserved.quantity(), reserved.unitPriceCents());
        }
        return OrderMapper.toResponse(orderRepository.saveAndFlush(order));
    }

    public OrderResponse get(UUID id) {
        return OrderMapper.toResponse(
                orderRepository.findWithItemsById(id).orElseThrow(() -> new ResourceNotFoundException("Order", id)));
    }
}
