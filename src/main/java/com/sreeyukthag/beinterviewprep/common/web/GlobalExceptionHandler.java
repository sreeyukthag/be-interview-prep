package com.sreeyukthag.beinterviewprep.common.web;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.sreeyukthag.beinterviewprep.common.exception.ApiException;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiResponse<Void>> handleApiException(ApiException ex) {
        return respond(ex.getStatus(), ApiResponse.error(ex.getErrorCode(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<Void>> handleInvalidBody(MethodArgumentNotValidException ex) {
        List<ApiResponse.FieldError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiResponse.FieldError(error.getField(), error.getDefaultMessage()))
                .toList();
        return validationFailed(errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        List<ApiResponse.FieldError> errors = ex.getConstraintViolations().stream()
                .map(violation ->
                        new ApiResponse.FieldError(violation.getPropertyPath().toString(), violation.getMessage()))
                .toList();
        return validationFailed(errors);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return validationFailed(List.of(new ApiResponse.FieldError(ex.getName(), "has an invalid value")));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ApiResponse<Void>> handleMissingParameter(MissingServletRequestParameterException ex) {
        return validationFailed(List.of(new ApiResponse.FieldError(ex.getParameterName(), "is required")));
    }

    @ExceptionHandler(PropertyReferenceException.class)
    ResponseEntity<ApiResponse<Void>> handleUnknownSortProperty(PropertyReferenceException ex) {
        return validationFailed(
                List.of(new ApiResponse.FieldError("sort", "has an unknown property: " + ex.getPropertyName())));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiResponse<Void>> handleUnreadableBody(HttpMessageNotReadableException ex) {
        if (ex.getCause() instanceof MismatchedInputException mismatch
                && !mismatch.getPath().isEmpty()) {
            return validationFailed(List.of(new ApiResponse.FieldError(fieldPath(mismatch), "has an invalid value")));
        }
        return respond(
                HttpStatus.BAD_REQUEST, ApiResponse.error("MALFORMED_REQUEST", "Request body is missing or malformed"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return respond(HttpStatus.METHOD_NOT_ALLOWED, ApiResponse.error("METHOD_NOT_ALLOWED", ex.getMessage()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException ex) {
        return respond(HttpStatus.NOT_FOUND, ApiResponse.error("NOT_FOUND", "No endpoint for " + ex.getResourcePath()));
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ApiResponse<Void>> handleConcurrentModification(OptimisticLockingFailureException ex) {
        return respond(
                HttpStatus.CONFLICT,
                ApiResponse.error(
                        "CONCURRENT_MODIFICATION", "The resource was changed by another request; reload and retry"));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return respond(
                HttpStatus.INTERNAL_SERVER_ERROR, ApiResponse.error("INTERNAL_ERROR", "An unexpected error occurred"));
    }

    private static String fieldPath(MismatchedInputException ex) {
        return ex.getPath().stream()
                .map(JsonMappingException.Reference::getFieldName)
                .filter(Objects::nonNull)
                .collect(Collectors.joining("."));
    }

    private ResponseEntity<ApiResponse<Void>> validationFailed(List<ApiResponse.FieldError> errors) {
        return respond(
                HttpStatus.BAD_REQUEST, ApiResponse.error("VALIDATION_FAILED", "Request validation failed", errors));
    }

    private ResponseEntity<ApiResponse<Void>> respond(HttpStatus status, ApiResponse<Void> body) {
        return ResponseEntity.status(status).body(body);
    }
}
