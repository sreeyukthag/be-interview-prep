package com.sreeyukthag.beinterviewprep.orders.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.sreeyukthag.beinterviewprep.orders.dto.request.OrderItemRequest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderLineTest {

    private static final UUID LOW = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID HIGH = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void normaliseMergesRepeatedProductsAndSortsById() {
        List<OrderItemRequest> items =
                List.of(new OrderItemRequest(HIGH, 1), new OrderItemRequest(LOW, 2), new OrderItemRequest(HIGH, 3));

        List<OrderLine> lines = OrderLine.normalise(items);

        assertThat(lines).containsExactly(new OrderLine(LOW, 2), new OrderLine(HIGH, 4));
    }

    @Test
    void fingerprintIgnoresItemOrderAndSplitLines() {
        List<OrderLine> listed =
                OrderLine.normalise(List.of(new OrderItemRequest(LOW, 2), new OrderItemRequest(HIGH, 4)));
        List<OrderLine> reordered = OrderLine.normalise(
                List.of(new OrderItemRequest(HIGH, 1), new OrderItemRequest(LOW, 2), new OrderItemRequest(HIGH, 3)));

        String first = OrderLine.fingerprint(listed);
        String second = OrderLine.fingerprint(reordered);

        assertThat(first).isEqualTo(second).hasSize(64);
    }

    @Test
    void fingerprintChangesWithQuantity() {
        List<OrderLine> two = List.of(new OrderLine(LOW, 2));
        List<OrderLine> three = List.of(new OrderLine(LOW, 3));

        String first = OrderLine.fingerprint(two);
        String second = OrderLine.fingerprint(three);

        assertThat(first).isNotEqualTo(second);
    }
}
