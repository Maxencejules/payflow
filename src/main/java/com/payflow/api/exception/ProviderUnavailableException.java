package com.payflow.api.exception;

/** A simulated provider failure before accepting an operation. */
public class ProviderUnavailableException extends RuntimeException {
    public ProviderUnavailableException(String message) { super(message); }
    public ProviderUnavailableException(String message, Throwable cause) { super(message, cause); }
}
