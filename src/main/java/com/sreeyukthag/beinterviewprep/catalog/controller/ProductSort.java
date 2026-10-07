package com.sreeyukthag.beinterviewprep.catalog.controller;

import com.sreeyukthag.beinterviewprep.common.exception.BadRequestException;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

final class ProductSort {

    static final Set<String> SORTABLE_FIELDS = Set.of("name", "category", "priceCents", "stock", "rating", "createdAt");

    private ProductSort() {}

    /** Rejects unknown sort fields and appends id so rows with equal sort values page deterministically. */
    static Pageable validated(Pageable pageable) {
        pageable.getSort().stream()
                .map(Sort.Order::getProperty)
                .filter(property -> !SORTABLE_FIELDS.contains(property))
                .findFirst()
                .ifPresent(property -> {
                    throw new BadRequestException(
                            "INVALID_SORT",
                            "Cannot sort by '%s'; sortable fields are %s"
                                    .formatted(
                                            property,
                                            SORTABLE_FIELDS.stream().sorted().toList()));
                });
        Sort sort = pageable.getSort().and(Sort.by("id"));
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
    }
}
