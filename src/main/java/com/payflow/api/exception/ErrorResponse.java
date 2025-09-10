package com.payflow.api.exception;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Standard error response structure for API errors.
 * Provides consistent error format across all endpoints.
 */
@Data
@Builder
public class ErrorResponse {

    /** Error message describing what went wrong */
    private String message;

    /** HTTP status code */
    private int status;

    /** Timestamp when the error occurred */
    private LocalDateTime timestamp;

    /** Field-specific validation errors (optional) */
    private Map<String, String> errors;

    /** Request path that caused the error (optional) */
    private String path;

    /** Unique error tracking ID (optional) */
    private String errorId;
}