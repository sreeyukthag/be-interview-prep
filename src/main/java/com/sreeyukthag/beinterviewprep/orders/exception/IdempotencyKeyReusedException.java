package com.sreeyukthag.beinterviewprep.orders.exception;

import com.sreeyukthag.beinterviewprep.common.exception.ApiException;
import org.springframework.http.HttpStatus;

public class IdempotencyKeyReusedException extends ApiException {

    public IdempotencyKeyReusedException(String idempotencyKey) {
        super(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "IDEMPOTENCY_KEY_REUSED",
                "Idempotency-Key '%s' was already used for a different order; use a new key for a new order"
                        .formatted(idempotencyKey));
    }
}
