package com.sreeyukthag.beinterviewprep.catalog.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record ProductRequest(
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 50) String category,
        @NotNull @PositiveOrZero Long priceCents,
        @NotNull @PositiveOrZero Integer stock,
        @NotNull @DecimalMin("0.0") @DecimalMax("5.0") Double rating) {}
