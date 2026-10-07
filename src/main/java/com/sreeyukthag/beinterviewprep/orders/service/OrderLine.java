package com.sreeyukthag.beinterviewprep.orders.service;

import com.sreeyukthag.beinterviewprep.orders.dto.request.OrderItemRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

/** One product per line, after merging repeated products in a request. */
record OrderLine(UUID productId, int quantity) {

    /**
     * Merges repeated products and sorts by product id. Every order then locks product rows in the same order, so
     * two orders for the same products cannot deadlock waiting on each other's rows.
     */
    static List<OrderLine> normalise(List<OrderItemRequest> items) {
        Map<UUID, Integer> quantities = new TreeMap<>();
        items.forEach(item -> quantities.merge(item.productId(), item.quantity(), Integer::sum));
        return quantities.entrySet().stream()
                .map(entry -> new OrderLine(entry.getKey(), entry.getValue()))
                .toList();
    }

    /** SHA-256 of the normalised lines, so a retry that lists the same items in another order still matches. */
    static String fingerprint(List<OrderLine> lines) {
        String canonical = lines.stream()
                .map(line -> line.productId() + ":" + line.quantity())
                .collect(Collectors.joining(";"));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
