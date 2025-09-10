package com.payflow.api.model;

/**
 * Enumeration of possible payment states throughout the payment lifecycle.
 *
 * State transitions:
 * PENDING -> PROCESSING -> COMPLETED
 * PENDING -> PROCESSING -> FAILED
 * COMPLETED -> REFUNDED
 * COMPLETED -> PARTIALLY_REFUNDED
 */
public enum PaymentStatus {
    /** Initial state when payment is created */
    PENDING,

    /** Payment is being processed by the provider */
    PROCESSING,

    /** Payment successfully completed */
    COMPLETED,

    /** Payment failed due to an error */
    FAILED,

    /** Payment cancelled by user or system */
    CANCELLED,

    /** Full amount has been refunded */
    REFUNDED,

    /** Partial amount has been refunded */
    PARTIALLY_REFUNDED
}