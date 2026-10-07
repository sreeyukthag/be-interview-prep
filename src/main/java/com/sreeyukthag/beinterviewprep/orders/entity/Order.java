package com.sreeyukthag.beinterviewprep.orders.entity;

import com.sreeyukthag.beinterviewprep.common.persistence.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "orders")
public class Order extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(name = "total_cents", nullable = false)
    private long totalCents;

    @Column(name = "idempotency_key", nullable = false, length = 64, updatable = false)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64, updatable = false)
    private String requestHash;

    @Version
    @Column(nullable = false)
    private long version;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    public static Order placed(String idempotencyKey, String requestHash) {
        Order order = new Order();
        order.status = OrderStatus.PLACED;
        order.idempotencyKey = idempotencyKey;
        order.requestHash = requestHash;
        return order;
    }

    public void addItem(UUID productId, int quantity, long unitPriceCents) {
        OrderItem item = new OrderItem(this, productId, quantity, unitPriceCents);
        items.add(item);
        totalCents += item.lineTotalCents();
    }
}
