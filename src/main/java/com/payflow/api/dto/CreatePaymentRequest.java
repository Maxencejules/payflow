package com.payflow.api.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Data Transfer Object for creating a new payment.
 * Contains all required information to initiate a payment transaction.
 * Includes validation constraints to ensure data integrity.
 */
@Data
public class CreatePaymentRequest {

    /**
     * Payment amount in the currency's smallest unit.
     * Must be greater than 0 for valid payment.
     */
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    private BigDecimal amount;

    /**
     * ISO 4217 currency code.
     * Must be exactly 3 characters (e.g., USD, EUR).
     */
    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be 3 characters")
    private String currency;

    /**
     * Customer email for payment notifications.
     * Must be a valid email format.
     */
    @NotBlank(message = "Customer email is required")
    @Email(message = "Invalid email format")
    private String customerEmail;

    /**
     * Optional customer identifier for reference.
     */
    private String customerId;

    /**
     * Optional payment description for customer reference.
     */
    private String description;
}