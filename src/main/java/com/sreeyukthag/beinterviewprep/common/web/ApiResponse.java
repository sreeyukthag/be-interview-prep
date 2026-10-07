package com.sreeyukthag.beinterviewprep.common.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success, T data, String message, String errorCode, List<FieldError> errors, Instant timestamp) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, null, null, Instant.now());
    }

    public static <T> ApiResponse<T> ok(T data, String message) {
        return new ApiResponse<>(true, data, message, null, null, Instant.now());
    }

    public static ApiResponse<Void> error(String errorCode, String message) {
        return new ApiResponse<>(false, null, message, errorCode, null, Instant.now());
    }

    public static ApiResponse<Void> error(String errorCode, String message, List<FieldError> errors) {
        return new ApiResponse<>(false, null, message, errorCode, errors, Instant.now());
    }

    public record FieldError(String field, String message) {}
}
