package com.sreeyukthag.beinterviewprep.catalog.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record ProductFilter(
        @Size(max = 50) String category,
        @PositiveOrZero Long minPrice,
        @PositiveOrZero Long maxPrice,
        Boolean inStock,
        @Size(max = 100) String q) {

    public static ProductFilter none() {
        return new ProductFilter(null, null, null, null, null);
    }

    @AssertTrue(message = "minPrice must not be greater than maxPrice")
    public boolean isPriceRangeValid() {
        return minPrice == null || maxPrice == null || minPrice <= maxPrice;
    }
}
