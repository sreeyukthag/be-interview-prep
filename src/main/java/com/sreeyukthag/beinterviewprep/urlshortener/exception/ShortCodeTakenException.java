package com.sreeyukthag.beinterviewprep.urlshortener.exception;

import com.sreeyukthag.beinterviewprep.common.exception.ConflictException;

public class ShortCodeTakenException extends ConflictException {

    public ShortCodeTakenException(String code) {
        super("SHORT_CODE_TAKEN", "Short code is already taken: " + code);
    }
}
