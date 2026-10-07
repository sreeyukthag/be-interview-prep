package com.sreeyukthag.beinterviewprep.catalog.repository;

import com.sreeyukthag.beinterviewprep.catalog.dto.request.ProductFilter;
import com.sreeyukthag.beinterviewprep.catalog.entity.Product;
import com.sreeyukthag.beinterviewprep.catalog.mapper.ProductMapper;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

/** One factory per filter; each returns {@code null} when its parameter is absent, which {@code allOf} skips. */
public final class ProductSpecifications {

    private static final char LIKE_ESCAPE = '\\';

    private ProductSpecifications() {}

    public static Specification<Product> matching(ProductFilter filter) {
        return Specification.allOf(
                hasCategory(filter.category()),
                priceAtLeast(filter.minPrice()),
                priceAtMost(filter.maxPrice()),
                inStockOnly(filter.inStock()),
                nameContains(filter.q()));
    }

    static Specification<Product> hasCategory(String category) {
        if (!StringUtils.hasText(category)) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("category"), ProductMapper.normalizeCategory(category));
    }

    static Specification<Product> priceAtLeast(Long minPriceCents) {
        if (minPriceCents == null) {
            return null;
        }
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("priceCents"), minPriceCents);
    }

    static Specification<Product> priceAtMost(Long maxPriceCents) {
        if (maxPriceCents == null) {
            return null;
        }
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("priceCents"), maxPriceCents);
    }

    static Specification<Product> inStockOnly(Boolean inStock) {
        if (!Boolean.TRUE.equals(inStock)) {
            return null;
        }
        return (root, query, cb) -> cb.greaterThan(root.get("stock"), 0);
    }

    static Specification<Product> nameContains(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String pattern = "%" + escapeLikeWildcards(text.trim().toLowerCase(Locale.ROOT)) + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("name")), pattern, LIKE_ESCAPE);
    }

    private static String escapeLikeWildcards(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
