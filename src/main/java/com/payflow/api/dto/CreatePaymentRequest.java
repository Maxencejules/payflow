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
     * Major currency units; at most 17 integer and 2 fractional digits.
     * The service also checks the currency-specific fraction limit.
     */
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    @Digits(integer = 17, fraction = 2, message = "Amount supports up to 17 integer and 2 fractional digits")
    private BigDecimal amount;

    /**
     * ISO 4217 currency code.
     * Must be exactly 3 characters (e.g., USD, EUR).
     */
    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be 3 characters")
    @Pattern(regexp = "[A-Za-z]{3}", message = "Currency must contain 3 ASCII letters")
    private String currency;

    /**
     * Customer email for payment notifications.
     * Must be a valid email format.
     */
    @NotBlank(message = "Customer email is required")
    @Email(message = "Invalid email format")
    @Size(max = 254, message = "Customer email is too long")
    private String customerEmail;

    /**
     * Optional customer identifier for reference.
     */
    @Size(max = 100, message = "Customer ID is too long")
    private String customerId;

    /**
     * Optional payment description for customer reference.
     */
    @Size(max = 255, message = "Description is too long")
    private String description;
}
