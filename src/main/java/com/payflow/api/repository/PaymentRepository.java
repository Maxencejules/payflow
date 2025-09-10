package com.payflow.api.repository;

import com.payflow.api.model.Payment;
import com.payflow.api.model.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for Payment entity operations.
 * Extends JpaRepository to provide CRUD operations and custom query methods.
 * Spring Data JPA will automatically provide the implementation.
 */
@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    /**
     * Find a payment by its idempotency key.
     * Used to prevent duplicate payment processing.
     *
     * @param idempotencyKey The unique idempotency key
     * @return Optional containing the payment if found
     */
    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    /**
     * Find a payment by the provider's payment ID.
     * Used for webhook processing and reconciliation.
     *
     * @param providerPaymentId External payment ID from provider
     * @return Optional containing the payment if found
     */
    Optional<Payment> findByProviderPaymentId(String providerPaymentId);

    /**
     * Find all payments for a customer email, ordered by creation date.
     * Used to display customer payment history.
     *
     * @param customerEmail Customer's email address
     * @return List of payments sorted by newest first
     */
    List<Payment> findByCustomerEmailOrderByCreatedAtDesc(String customerEmail);

    /**
     * Find all payments with a specific status.
     * Useful for batch processing and reporting.
     *
     * @param status Payment status to filter by
     * @return List of payments with the given status
     */
    List<Payment> findByStatus(PaymentStatus status);
}
