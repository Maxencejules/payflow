package com.payflow.api.service;

import com.payflow.api.exception.PaymentException;
import com.payflow.api.exception.ProviderUnavailableException;
import com.payflow.api.model.Payment;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentProviderServiceTest {
    private final PaymentProviderService provider = new PaymentProviderService();

    @Test
    void initializationUsesTheAssignedPaymentIdDeterministically() {
        Payment payment = new Payment();
        payment.setId(UUID.randomUUID());
        assertThat(provider.createPayment(payment)).isEqualTo("sim_" + payment.getId());
        assertThat(provider.createPayment(payment)).isEqualTo("sim_" + payment.getId());
    }

    @Test
    void methodsHaveExplicitOutcomesAndOldReferencesStillWork() {
        assertThat(provider.confirmPayment("pi_legacy", "pm_success")).isTrue();
        assertThat(provider.confirmPayment("pi_legacy", "pm_decline")).isFalse();
        assertThatThrownBy(() -> provider.confirmPayment("pi_legacy", "pm_unavailable"))
                .isInstanceOf(ProviderUnavailableException.class);
        assertThatThrownBy(() -> provider.confirmPayment("pi_legacy", "unknown"))
                .isInstanceOf(PaymentException.class);
    }

    @Test
    void interruptedOperationsFailBeforeAcceptanceAndPreserveTheFlag() {
        Payment payment = new Payment();
        payment.setId(UUID.randomUUID());
        try {
            Thread.currentThread().interrupt();
            assertThatThrownBy(() -> provider.createPayment(payment)).isInstanceOf(ProviderUnavailableException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            assertThatThrownBy(() -> provider.confirmPayment("pi_legacy", "pm_success"))
                    .isInstanceOf(ProviderUnavailableException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }
}
