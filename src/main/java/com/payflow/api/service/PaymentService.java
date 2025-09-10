package com.payflow.api.service;

import com.payflow.api.dto.CreatePaymentRequest;
import com.payflow.api.dto.PaymentResponse;
import com.payflow.api.exception.PaymentException;
import com.payflow.api.model.Payment;
import com.payflow.api.model.PaymentStatus;
import com.payflow.api.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Core payment service handling business logic for payment operations.
 * Manages payment lifecycle, integrates with payment providers,
 * and ensures data consistency with transactional support.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentProviderService providerService;

    /**
     * Creates a new payment with idempotency support.
     * Prevents duplicate payments by checking idempotency key.
     *
     * @param request Payment creation request with amount and customer details
     * @param idempotencyKey Optional key to prevent duplicate payments
     * @return PaymentResponse with created payment details
     */
    @Transactional
    public PaymentResponse createPayment(CreatePaymentRequest request, String idempotencyKey) {
        log.info("Creating payment: amount={}, currency={}, customer={}",
                request.getAmount(), request.getCurrency(), request.getCustomerEmail());

        // Check for existing payment with same idempotency key
        if (idempotencyKey != null) {
            var existingPayment = paymentRepository.findByIdempotencyKey(idempotencyKey);
            if (existingPayment.isPresent()) {
                log.info("Returning existing payment for idempotency key: {}", idempotencyKey);
                return PaymentResponse.fromPayment(existingPayment.get());
            }
        }

        // Create new payment entity
        Payment payment = new Payment();
        payment.setAmount(request.getAmount());
        payment.setCurrency(request.getCurrency().toUpperCase());
        payment.setCustomerEmail(request.getCustomerEmail());
        payment.setCustomerId(request.getCustomerId());
        payment.setDescription(request.getDescription());
        payment.setStatus(PaymentStatus.PENDING);
        payment.setIdempotencyKey(idempotencyKey);

        // Initialize payment with provider
        try {
            String providerId = providerService.createPayment(payment);
            payment.setProviderPaymentId(providerId);
            log.info("Payment initialized with provider: {}", providerId);
        } catch (Exception e) {
            log.error("Failed to create payment with provider", e);
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason("Provider error: " + e.getMessage());
        }

        // Save payment to database
        payment = paymentRepository.save(payment);
        log.info("Payment created successfully: {}", payment.getId());

        return PaymentResponse.fromPayment(payment);
    }

    /**
     * Confirms a pending payment by processing it with the payment provider.
     * Updates payment status based on provider response.
     *
     * @param paymentId UUID of the payment to confirm
     * @param paymentMethodId Payment method to use for charging
     * @return PaymentResponse with updated payment status
     * @throws PaymentException if payment not found or in invalid state
     */
    @Transactional
    public PaymentResponse confirmPayment(UUID paymentId, String paymentMethodId) {
        log.info("Confirming payment: {}", paymentId);

        // Retrieve payment from database
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentException("Payment not found: " + paymentId));

        // Validate payment state
        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new PaymentException(
                    "Payment cannot be confirmed in status: " + payment.getStatus()
            );
        }

        // Update payment with processing status
        payment.setStatus(PaymentStatus.PROCESSING);
        payment.setPaymentMethodId(paymentMethodId);

        // Process payment with provider
        try {
            boolean success = providerService.confirmPayment(
                    payment.getProviderPaymentId(),
                    paymentMethodId
            );

            if (success) {
                payment.setStatus(PaymentStatus.COMPLETED);
                payment.setCompletedAt(LocalDateTime.now());
                log.info("Payment confirmed successfully: {}", paymentId);
            } else {
                payment.setStatus(PaymentStatus.FAILED);
                payment.setFailureReason("Payment confirmation failed");
                log.warn("Payment confirmation failed: {}", paymentId);
            }
        } catch (Exception e) {
            log.error("Error confirming payment: {}", paymentId, e);
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason(e.getMessage());
        }

        // Save updated payment
        payment = paymentRepository.save(payment);
        return PaymentResponse.fromPayment(payment);
    }

    /**
     * Retrieves a payment by its ID.
     *
     * @param paymentId UUID of the payment
     * @return PaymentResponse with payment details
     * @throws PaymentException if payment not found
     */
    public PaymentResponse getPayment(UUID paymentId) {
        log.debug("Fetching payment: {}", paymentId);

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentException("Payment not found: " + paymentId));

        return PaymentResponse.fromPayment(payment);
    }

    /**
     * Retrieves all payments for a customer by email.
     * Returns payments sorted by creation date (newest first).
     *
     * @param email Customer email address
     * @return List of payments for the customer
     */
    public List<PaymentResponse> getPaymentsByEmail(String email) {
        log.debug("Fetching payments for customer: {}", email);

        return paymentRepository.findByCustomerEmailOrderByCreatedAtDesc(email)
                .stream()
                .map(PaymentResponse::fromPayment)
                .collect(Collectors.toList());
    }

    /**
     * Checks if the payment service is healthy.
     * Verifies database connectivity and provider availability.
     *
     * @return true if service is operational
     */
    public boolean isHealthy() {
        try {
            // Check database connectivity
            paymentRepository.count();
            // Check provider connectivity
            return providerService.isHealthy();
        } catch (Exception e) {
            log.error("Health check failed", e);
            return false;
        }
    }
}