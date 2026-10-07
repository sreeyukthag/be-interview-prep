package com.sreeyukthag.beinterviewprep.catalog.stock;

import java.util.UUID;

public record ReservedItem(UUID productId, int quantity, long unitPriceCents) {}
