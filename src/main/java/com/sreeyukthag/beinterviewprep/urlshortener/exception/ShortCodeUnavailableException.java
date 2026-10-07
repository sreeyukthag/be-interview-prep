package com.sreeyukthag.beinterviewprep.urlshortener.exception;

import com.sreeyukthag.beinterviewprep.common.exception.ApiException;
import org.springframework.http.HttpStatus;

public class ShortCodeUnavailableException extends ApiException {

    public ShortCodeUnavailableException(int attempts) {
        super(
                HttpStatus.SERVICE_UNAVAILABLE,
                "SHORT_CODE_UNAVAILABLE",
                "Could not allocate a unique short code after %d attempts; please retry".formatted(attempts));
    }
}
