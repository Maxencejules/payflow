package com.payflow.api.exception;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class GlobalExceptionHandler {
    private final Clock clock;

    @ExceptionHandler(PaymentException.class)
    public ResponseEntity<ErrorResponse> handlePayment(PaymentException error, WebRequest request) {
        return response(error.getStatus(), error.getCode(), error.getMessage(), null, request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException error, WebRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        error.getBindingResult().getAllErrors().forEach(violation -> fields.put(
                violation instanceof FieldError field ? field.getField() : violation.getObjectName(),
                violation.getDefaultMessage()));
        return response(HttpStatus.BAD_REQUEST, "invalid_request", "Validation failed", fields, request);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> handleMalformed(Exception error, WebRequest request) {
        return response(HttpStatus.BAD_REQUEST, "invalid_request", "Malformed request", null, request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleMissingRoute(NoResourceFoundException error, WebRequest request) {
        return response(HttpStatus.NOT_FOUND, "route_not_found", "Route not found", null, request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethod(HttpRequestMethodNotSupportedException error, WebRequest request) {
        return response(HttpStatus.METHOD_NOT_ALLOWED, "method_not_allowed", "Method not allowed", null, request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaType(HttpMediaTypeNotSupportedException error, WebRequest request) {
        return response(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "unsupported_media_type", "Use application/json", null, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception error, WebRequest request) {
        log.error("Unexpected payment API error", error);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error",
                "An unexpected error occurred", null, request);
    }

    private ResponseEntity<ErrorResponse> response(HttpStatus status, String code, String message,
            Map<String, String> errors, WebRequest request) {
        return ResponseEntity.status(status).body(ErrorResponse.builder().status(status.value()).code(code)
                .message(message).timestamp(LocalDateTime.now(clock)).errors(errors)
                .path(request.getDescription(false).replace("uri=", ""))
                .errorId(UUID.randomUUID().toString()).build());
    }
}
