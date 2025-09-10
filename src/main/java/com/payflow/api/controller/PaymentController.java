package com.payflow.api.controller;

import com.payflow.api.dto.ConfirmPaymentRequest;
import com.payflow.api.dto.CreatePaymentRequest;
import com.payflow.api.dto.PaymentResponse;
import com.payflow.api.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for payment operations.
 * Provides endpoints for creating, confirming, and retrieving payments.
 * All endpoints follow RESTful conventions and return appropriate HTTP status codes.
 */
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*") // In production, specify allowed origins
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * Creates a new payment with optional idempotency support.
     *
     * POST /api/v1/payments
     *
     * @param request Payment creation request with amount and customer details
     * @param idempotencyKey Optional header to prevent duplicate payments
     * @return ResponseEntity with created payment details and 201 status
     */
    @PostMapping
    public ResponseEntity<PaymentResponse> createPayment(
            @Valid @RequestBody CreatePaymentRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {

        log.info("POST /api/v1/payments - Creating payment for amount: {} {}",
                request.getAmount(), request.getCurrency());

        PaymentResponse response = paymentService.createPayment(request, idempotencyKey);

        log.info("Payment created successfully: {}", response.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Confirms a pending payment by processing it with the payment provider.
     *
     * POST /api/v1/payments/{paymentId}/confirm
     *
     * @param paymentId UUID of the payment to confirm
     * @param request Confirmation request with payment method details
     * @return ResponseEntity with updated payment details
     */
    @PostMapping("/{paymentId}/confirm")
    public ResponseEntity<PaymentResponse> confirmPayment(
            @PathVariable UUID paymentId,
            @Valid @RequestBody ConfirmPaymentRequest request) {

        log.info("POST /api/v1/payments/{}/confirm - Confirming payment", paymentId);

        PaymentResponse response = paymentService.confirmPayment(
                paymentId,
                request.getPaymentMethodId()
        );

        log.info("Payment {} confirmation result: {}", paymentId, response.getStatus());
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves a payment by its ID.
     *
     * GET /api/v1/payments/{paymentId}
     *
     * @param paymentId UUID of the payment to retrieve
     * @return ResponseEntity with payment details
     */
    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentResponse> getPayment(@PathVariable UUID paymentId) {
        log.info("GET /api/v1/payments/{} - Retrieving payment", paymentId);

        PaymentResponse response = paymentService.getPayment(paymentId);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves all payments for a customer by email address.
     *
     * GET /api/v1/payments/customer/{email}
     *
     * @param email Customer email address
     * @return ResponseEntity with list of customer payments
     */
    @GetMapping("/customer/{email}")
    public ResponseEntity<List<PaymentResponse>> getCustomerPayments(
            @PathVariable String email) {

        log.info("GET /api/v1/payments/customer/{} - Retrieving customer payments", email);

        List<PaymentResponse> payments = paymentService.getPaymentsByEmail(email);

        log.info("Found {} payments for customer: {}", payments.size(), email);
        return ResponseEntity.ok(payments);
    }

    /**
     * Health check endpoint for monitoring.
     * Returns service status and basic metrics.
     *
     * GET /api/v1/payments/health
     *
     * @return ResponseEntity with health status
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "Payment Service");
        health.put("timestamp", System.currentTimeMillis());

        // Check service health
        boolean isHealthy = paymentService.isHealthy();
        if (!isHealthy) {
            health.put("status", "DOWN");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(health);
        }

        return ResponseEntity.ok(health);
    }

    /**
     * Returns API information.
     *
     * GET /api/v1/payments
     *
     * @return ResponseEntity with API info
     */
    @GetMapping
    public ResponseEntity<Map<String, String>> apiInfo() {
        Map<String, String> info = new HashMap<>();
        info.put("service", "PayFlow Payment API");
        info.put("version", "1.0.0");
        info.put("description", "Payment processing service");
        info.put("documentation", "/api/docs");

        return ResponseEntity.ok(info);
    }
}