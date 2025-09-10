package com.payflow.api.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Payment entity representing a payment transaction in the system.
 * This class maps to the 'payments' table in the database.
 *
 * @author Maxence JUles
 * @version 1.0
 * @since 2025-09-10
 */
@Entity
@Table(name = "payments")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Payment {

    /**
     * Unique identifier for the payment.
     * Auto-generated UUID to ensure global uniqueness.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Payment amount in the smallest currency unit.
     * For USD, this would be cents (e.g., 1000 = $10.00).
     * Using BigDecimal for precise monetary calculations.
     */
    @Column(nullable = false)
    private BigDecimal amount;

    /**
     * ISO 4217 currency code (e.g., USD, EUR, GBP).
     * Limited to 3 characters as per ISO standard.
     */
    @Column(nullable = false, length = 3)
    private String currency;

    /**
     * Current status of the payment in its lifecycle.
     * Defaults to PENDING when a payment is created.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status = PaymentStatus.PENDING;

    /**
     * External customer identifier from the client system.
     * Optional field for tracking customer payments.
     */
    @Column(name = "customer_id")
    private String customerId;

    /**
     * Customer email address for payment receipts and notifications.
     */
    @Column(name = "customer_email")
    private String customerEmail;

    /**
     * Human-readable description of the payment.
     * Appears on customer statements and invoices.
     */
    private String description;

    /**
     * Payment method identifier used for this transaction.
     * References the payment instrument (card, bank, etc.).
     */
    @Column(name = "payment_method_id")
    private String paymentMethodId;

    /**
     * External payment ID from the payment provider (Stripe, PayPal, etc.).
     * Used for reconciliation and provider API calls.
     */
    @Column(name = "provider_payment_id", unique = true)
    private String providerPaymentId;

    /**
     * Reason for payment failure, if applicable.
     * Null when payment is successful.
     */
    @Column(name = "failure_reason")
    private String failureReason;

    /**
     * Idempotency key to prevent duplicate payment processing.
     * Ensures exactly-once semantics for payment creation.
     */
    @Column(name = "idempotency_key", unique = true)
    private String idempotencyKey;

    /**
     * Timestamp when the payment was created.
     * Automatically set by Hibernate on entity creation.
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Timestamp when the payment was completed successfully.
     * Null for pending or failed payments.
     */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}