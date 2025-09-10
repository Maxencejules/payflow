package com.payflow.api.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Custom exception for payment-related errors.
 * Automatically returns HTTP 400 Bad Request when thrown from controllers.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class PaymentException extends RuntimeException {

    /**
     * Constructs a new payment exception with the specified detail message.
     *
     * @param message The detail message explaining the error
     */
    public PaymentException(String message) {
        super(message);
    }

    /**
     * Constructs a new payment exception with the specified detail message and cause.
     *
     * @param message The detail message explaining the error
     * @param cause The cause of the exception
     */
    public PaymentException(String message, Throwable cause) {
        super(message, cause);
    }
}