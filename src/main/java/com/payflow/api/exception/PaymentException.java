package com.payflow.api.exception;

import org.springframework.http.HttpStatus;

/**
 * Custom exception for payment-related errors.
 * Stable API status/code; the advice renders a JSON error without provider details.
 */
public class PaymentException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    /**
     * Constructs a new payment exception with the specified detail message.
     *
     * @param message The detail message explaining the error
     */
    public PaymentException(String message) {
        this(HttpStatus.BAD_REQUEST, "invalid_request", message);
    }

    /**
     * Constructs a new payment exception with the specified detail message and cause.
     *
     * @param message The detail message explaining the error
     * @param cause The cause of the exception
     */
    public PaymentException(String message, Throwable cause) {
        this(HttpStatus.BAD_REQUEST, "invalid_request", message, cause);
    }

    public PaymentException(HttpStatus status, String code, String message) {
        this(status, code, message, null);
    }

    public PaymentException(HttpStatus status, String code, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
}
