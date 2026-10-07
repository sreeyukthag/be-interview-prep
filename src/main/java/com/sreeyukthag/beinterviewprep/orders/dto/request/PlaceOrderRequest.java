package com.sreeyukthag.beinterviewprep.orders.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record PlaceOrderRequest(
        @NotNull @Size(min = 1, max = 50) List<@Valid @NotNull OrderItemRequest> items) {}
