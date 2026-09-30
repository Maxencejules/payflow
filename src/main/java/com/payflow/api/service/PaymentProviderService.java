package com.payflow.api.service;

import com.payflow.api.exception.PaymentException;
import com.payflow.api.exception.ProviderUnavailableException;
import com.payflow.api.model.Payment;
import org.springframework.stereotype.Service;

import java.util.Objects;

/** Pure simulation: no card data, network calls, random outcomes, or monetary effects. */
@Service
public class PaymentProviderService {
    public String createPayment(Payment payment) {
        checkInterrupted();
        return "sim_" + Objects.requireNonNull(payment.getId(), "Payment must have an assigned UUID");
    }

    public boolean confirmPayment(String providerPaymentId, String paymentMethodId) {
        checkInterrupted();
        Objects.requireNonNull(providerPaymentId, "Payment must have a provider reference");
        // Old pending rows with pi_ references remain confirmable; this simulator has no external state.
        return switch (paymentMethodId) {
            case "pm_success" -> true;
            case "pm_decline" -> false;
            case "pm_unavailable" -> throw new ProviderUnavailableException("Unavailable before acceptance");
            default -> throw new PaymentException("Unknown simulated payment method");
        };
    }

    public boolean isHealthy() {
        return true; // Reports simulation health, never the availability of a real gateway.
    }

    private static void checkInterrupted() {
        if (Thread.currentThread().isInterrupted()) {
            // Preserve cancellation and do not continue simulated acceptance.
            throw new ProviderUnavailableException("Interrupted before acceptance");
        }
    }
}
