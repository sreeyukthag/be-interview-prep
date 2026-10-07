package com.sreeyukthag.beinterviewprep.urlshortener.exception;

import com.sreeyukthag.beinterviewprep.common.exception.ApiException;
import org.springframework.http.HttpStatus;

public class ShortUrlExpiredException extends ApiException {

    public ShortUrlExpiredException(String code) {
        super(HttpStatus.GONE, "SHORT_URL_EXPIRED", "Short URL has expired: " + code);
    }
}
