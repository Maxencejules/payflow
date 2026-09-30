package com.payflow.api.service;

import com.payflow.api.dto.CreatePaymentRequest;
import com.payflow.api.dto.PaymentResponse;
import com.payflow.api.exception.PaymentException;
import com.payflow.api.exception.ProviderUnavailableException;
import com.payflow.api.model.Payment;
import com.payflow.api.model.PaymentStatus;
import com.payflow.api.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** PostgreSQL-backed lifecycle for a deterministic simulated payment provider. */
@Service
@RequiredArgsConstructor
public class PaymentService {
    private final PaymentRepository paymentRepository;
    private final PaymentProviderService providerService;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PaymentResponse createPayment(CreatePaymentRequest request, String idempotencyKey) {
        String currency = validateCurrency(request);
        String email = normalizeEmail(request.getCustomerEmail());
        // Unicode case conversion can expand input that passed the raw DTO length check.
        if (email.length() > 254) {
            throw invalid("Normalized customer email is too long");
        }
        if (idempotencyKey != null) {
            if (!idempotencyKey.matches("[A-Za-z0-9._:-]{1,100}")) {
                throw invalid("Idempotency-Key must contain 1 to 100 ASCII letters, digits, '.', '_', ':', or '-'");
            }
            // Same database transaction/connection as JPA. A hash collision only serializes unrelated keys.
            // READ_COMMITTED gives the post-wait lookup a fresh snapshot of the winner's committed row.
            jdbc.execute((ConnectionCallback<Void>) connection -> {
                try (var statement = connection.prepareStatement(
                        "select pg_advisory_xact_lock(hashtextextended(?, 0))")) {
                    statement.setString(1, idempotencyKey);
                    statement.execute();
                }
                return null;
            });
            var existing = paymentRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                Payment stored = existing.get();
                if (stored.getAmount().compareTo(request.getAmount()) != 0
                        || !stored.getCurrency().equalsIgnoreCase(currency)
                        || !email.equals(normalizeEmail(stored.getCustomerEmail()))
                        || !Objects.equals(stored.getCustomerId(), request.getCustomerId())
                        || !Objects.equals(stored.getDescription(), request.getDescription())) {
                    throw new PaymentException(HttpStatus.CONFLICT, "idempotency_conflict",
                            "Idempotency-Key was already used with a different payment payload");
                }
                return PaymentResponse.fromPayment(stored);
            }
        }
        Payment payment = new Payment();
        payment.setAmount(request.getAmount());
        payment.setCurrency(currency);
        payment.setCustomerEmail(email);
        payment.setCustomerId(request.getCustomerId());
        payment.setDescription(request.getDescription());
        payment.setStatus(PaymentStatus.PENDING);
        payment.setIdempotencyKey(idempotencyKey);
        payment.setCreatedAt(now());
        paymentRepository.saveAndFlush(payment); // Assign UUID before simulated initialization.
        try {
            payment.setProviderPaymentId(providerService.createPayment(payment));
        } catch (ProviderUnavailableException unavailable) {
            throw unavailable(unavailable); // Roll back row and key; no provider acceptance happened.
        }
        return PaymentResponse.fromPayment(payment);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PaymentResponse confirmPayment(UUID paymentId, String paymentMethodId) {
        Payment payment = paymentRepository.findByIdForUpdate(paymentId).orElseThrow(PaymentService::missing);
        if ((payment.getStatus() == PaymentStatus.COMPLETED || payment.getStatus() == PaymentStatus.FAILED)
                && payment.getPaymentMethodId() != null) {
            if (!payment.getPaymentMethodId().equals(paymentMethodId)) {
                throw new PaymentException(HttpStatus.CONFLICT, "confirmation_conflict",
                        "Payment was already confirmed with a different payment method");
            }
            return PaymentResponse.fromPayment(payment);
        }
        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new PaymentException(HttpStatus.CONFLICT, "state_conflict",
                    "Payment cannot be confirmed in status: " + payment.getStatus());
        }
        payment.setStatus(PaymentStatus.PROCESSING);
        payment.setPaymentMethodId(paymentMethodId);
        final boolean successful;
        try {
            successful = providerService.confirmPayment(payment.getProviderPaymentId(), paymentMethodId);
        } catch (ProviderUnavailableException unavailable) {
            throw unavailable(unavailable); // Restore PENDING, including method and timestamps.
        }
        payment.setStatus(successful ? PaymentStatus.COMPLETED : PaymentStatus.FAILED);
        payment.setCompletedAt(successful ? now() : null);
        payment.setFailureReason(successful ? null : "Payment was declined by the simulated provider");
        return PaymentResponse.fromPayment(payment);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPayment(UUID paymentId) {
        return PaymentResponse.fromPayment(paymentRepository.findById(paymentId).orElseThrow(PaymentService::missing));
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> getPaymentsByEmail(String email) {
        String normalized = normalizeEmail(email);
        return paymentRepository.findByCustomerEmailIgnoreCaseOrderByCreatedAtDesc(normalized)
                .stream()
                // Database case folding can collapse distinct ROOT-normalized Unicode addresses.
                .filter(payment -> Objects.equals(normalized, normalizeEmail(payment.getCustomerEmail())))
                .map(PaymentResponse::fromPayment).toList();
    }

    public boolean isHealthy() {
        try {
            paymentRepository.count();
            return providerService.isHealthy();
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    private LocalDateTime now() {
        // PostgreSQL stores microseconds; the original response must match later reads/replays.
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
    }

    private static String normalizeEmail(String email) {
        return email == null ? null : email.toLowerCase(Locale.ROOT);
    }

    private static String validateCurrency(CreatePaymentRequest request) {
        String code = request.getCurrency().toUpperCase(Locale.ROOT);
        final Currency currency;
        try {
            currency = Currency.getInstance(code);
        } catch (IllegalArgumentException invalidCode) {
            throw invalid("Currency must be a supported ISO 4217 code");
        }
        int digits = currency.getDefaultFractionDigits();
        if (digits < 0 || digits > 2) {
            throw invalid("Only currencies with zero to two minor-unit digits are supported");
        }
        if (request.getAmount().stripTrailingZeros().scale() > digits) {
            throw invalid("Amount has too many fractional digits for this currency");
        }
        return code;
    }

    private static PaymentException invalid(String message) {
        return new PaymentException(HttpStatus.BAD_REQUEST, "invalid_request", message);
    }

    private static PaymentException missing() {
        return new PaymentException(HttpStatus.NOT_FOUND, "payment_not_found", "Payment not found");
    }

    private static PaymentException unavailable(ProviderUnavailableException cause) {
        return new PaymentException(HttpStatus.SERVICE_UNAVAILABLE, "provider_unavailable",
                "Simulated provider unavailable before acceptance; retry is safe", cause);
    }
}
