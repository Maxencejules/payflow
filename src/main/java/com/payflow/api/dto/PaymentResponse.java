package com.payflow.api.dto;

import com.payflow.api.model.Payment;
import com.payflow.api.model.PaymentStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO containing payment information.
 * Used to return payment data to API clients.
 * Excludes sensitive internal fields from the Payment entity.
 */
@Data
@Builder
public class PaymentResponse {
    /** Unique payment identifier */
    private UUID id;

    /** Payment amount */
    private BigDecimal amount;

    /** Currency code */
    private String currency;

    /** Current payment status */
    private PaymentStatus status;

    /** Customer identifier */
    private String customerId;

    /** Customer email */
    private String customerEmail;

    /** Payment description */
    private String description;

    /** Failure reason if payment failed */
    private String failureReason;

    /** Creation timestamp */
    private LocalDateTime createdAt;

    /** Completion timestamp */
    private LocalDateTime completedAt;

    /**
     * Factory method to convert Payment entity to PaymentResponse DTO.
     * Maps only the fields that should be exposed to API clients.
     *
     * @param payment The payment entity to convert
     * @return PaymentResponse DTO with mapped fields
     */
    public static PaymentResponse fromPayment(Payment payment) {
        return PaymentResponse.builder()
                .id(payment.getId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .status(payment.getStatus())
                .customerId(payment.getCustomerId())
                .customerEmail(payment.getCustomerEmail())
                .description(payment.getDescription())
                .failureReason(payment.getFailureReason())
                .createdAt(payment.getCreatedAt())
                .completedAt(payment.getCompletedAt())
                .build();
    }
}
