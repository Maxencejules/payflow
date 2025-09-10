package com.payflow.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Request DTO for confirming a pending payment.
 * Contains the payment method details needed to complete the transaction.
 */
@Data
public class ConfirmPaymentRequest {

    /**
     * Payment method identifier to use for charging.
     * Could be a tokenized card, saved payment method, etc.
     * Required for payment confirmation.
     */
    @NotBlank(message = "Payment method ID is required")
    private String paymentMethodId;
}