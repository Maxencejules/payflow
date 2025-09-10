package com.payflow.api.service;

import com.payflow.api.model.Payment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Random;
import java.util.UUID;

/**
 * Mock payment provider service simulating external payment gateway.
 * In production, this would integrate with Stripe, PayPal, or other providers.
 * Currently returns mock responses for development and testing.
 */
@Service
@Slf4j
public class PaymentProviderService {

    private final Random random = new Random();

    /**
     * Simulates creating a payment with an external provider.
     * In production, this would call the actual payment provider API.
     *
     * @param payment Payment entity containing transaction details
     * @return Provider-specific payment ID
     * @throws RuntimeException Simulates provider errors (10% failure rate)
     */
    public String createPayment(Payment payment) {
        log.info("Creating payment with provider for amount: {} {}",
                payment.getAmount(), payment.getCurrency());

        // Simulate API latency (100-500ms)
        try {
            Thread.sleep(100 + random.nextInt(400));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Simulate occasional failures (10% failure rate for testing)
        if (random.nextInt(10) == 0) {
            log.error("Provider temporarily unavailable");
            throw new RuntimeException("Provider temporarily unavailable");
        }

        // Generate mock provider payment ID (similar to Stripe's format)
        String providerId = "pi_" + UUID.randomUUID().toString()
                .replace("-", "").substring(0, 24);

        log.info("Provider payment created successfully: {}", providerId);
        return providerId;
    }

    /**
     * Simulates confirming/capturing a payment with the provider.
     * In production, this would finalize the payment transaction.
     *
     * @param providerPaymentId Provider's payment identifier
     * @param paymentMethodId Payment method to charge
     * @return true if payment successful, false otherwise
     */
    public boolean confirmPayment(String providerPaymentId, String paymentMethodId) {
        log.info("Confirming payment {} with method {}",
                providerPaymentId, paymentMethodId);

        // Simulate API latency (200-500ms)
        try {
            Thread.sleep(200 + random.nextInt(300));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Simulate 95% success rate
        boolean success = random.nextInt(20) != 0;

        if (success) {
            log.info("Payment {} confirmed successfully", providerPaymentId);
        } else {
            log.warn("Payment {} confirmation failed", providerPaymentId);
        }

        return success;
    }

    /**
     * Health check for the payment provider connection.
     *
     * @return true if provider is reachable
     */
    public boolean isHealthy() {
        // In production, this would ping the provider's API
        return true;
    }
}