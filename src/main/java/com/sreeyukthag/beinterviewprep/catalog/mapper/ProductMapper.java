package com.sreeyukthag.beinterviewprep.catalog.mapper;

import com.sreeyukthag.beinterviewprep.catalog.dto.request.ProductRequest;
import com.sreeyukthag.beinterviewprep.catalog.dto.response.ProductResponse;
import com.sreeyukthag.beinterviewprep.catalog.entity.Product;
import java.util.Locale;

public final class ProductMapper {

    private ProductMapper() {}

    public static ProductResponse toResponse(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getCategory(),
                product.getPriceCents(),
                product.getStock(),
                product.getRating(),
                product.getCreatedAt(),
                product.getUpdatedAt());
    }

    public static void apply(ProductRequest request, Product product) {
        product.setName(request.name().trim());
        product.setCategory(normalizeCategory(request.category()));
        product.setPriceCents(request.priceCents());
        product.setStock(request.stock());
        product.setRating(request.rating());
    }

    public static String normalizeCategory(String category) {
        return category.trim().toLowerCase(Locale.ROOT);
    }
}
