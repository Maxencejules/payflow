package com.payflow.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
    @Size(max = 100, message = "Payment method ID is too long")
    private String paymentMethodId;
}
