package com.sreeyukthag.beinterviewprep.orders.dto.response;

import java.util.UUID;

public record OrderItemResponse(UUID productId, int quantity, long unitPriceCents, long lineTotalCents) {}
