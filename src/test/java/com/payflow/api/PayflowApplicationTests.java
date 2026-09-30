package com.payflow.api;

import com.jayway.jsonpath.JsonPath;
import com.payflow.api.exception.ProviderUnavailableException;
import com.payflow.api.model.Payment;
import com.payflow.api.model.PaymentStatus;
import com.payflow.api.repository.PaymentRepository;
import com.payflow.api.service.PaymentProviderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Requires PostgreSQL: no H2 substitute for transaction or lock behavior. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Timeout(30)
class PayflowApplicationTests {
    private static final String API = "/api/v1/payments";
    @Autowired MockMvc mvc;
    @Autowired PaymentRepository payments;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @MockitoSpyBean PaymentProviderService provider;

    @BeforeEach
    void deterministicProviderAndRealDatabase() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("PostgreSQL");
        }
        // Isolate service races from the original provider's random outcomes.
        doAnswer(call -> {
            Payment payment = call.getArgument(0);
            return "sim_" + (payment.getId() == null ? UUID.randomUUID() : payment.getId());
        }).when(provider).createPayment(any(Payment.class));
        doAnswer(call -> !"pm_decline".equals(call.getArgument(1)))
                .when(provider).confirmPayment(anyString(), anyString());
    }

    @Test
    void simultaneousSameKeyCreatesOnePaymentAndInitializesProviderOnce() throws Exception {
        String key = UUID.randomUUID().toString();
        String body = body("12.50", "USD", "Customer@example.com");
        List<MvcResult> results = overlap("lock table payments in share mode", null,
                List.of(() -> create(body, key), () -> create(body, key),
                        () -> create(body, key), () -> create(body, key)));
        for (MvcResult result : results) {
            assertThat(result.getResponse().getStatus()).isEqualTo(201);
            assertThat((String) read(result, "$.id")).isEqualTo(read(results.get(0), "$.id"));
            assertThat((String) read(result, "$.status")).isEqualTo("PENDING");
        }
        assertThat(jdbc.queryForObject("select count(*) from payments where idempotency_key = ?", Integer.class, key)).isEqualTo(1);
        verify(provider, times(1)).createPayment(any(Payment.class));
    }

    @Test
    void parallelDifferentPayloadsGiveOneCreationAndOneExplicitKeyConflict() throws Exception {
        String key = UUID.randomUUID().toString();
        List<MvcResult> results = overlap("lock table payments in share mode", null, List.of(
                () -> create(body("10", "USD", "customer@example.com"), key),
                () -> create(body("11", "USD", "customer@example.com"), key)));
        assertThat(results.stream().map(r -> r.getResponse().getStatus()).toList()).containsExactlyInAnyOrder(201, 409);
        MvcResult conflict = results.stream().filter(r -> r.getResponse().getStatus() == 409).findFirst().orElseThrow();
        assertThat((String) read(conflict, "$.code")).isEqualTo("idempotency_conflict");
        assertThat(jdbc.queryForObject("select count(*) from payments where idempotency_key = ?", Integer.class, key)).isEqualTo(1);
        verify(provider, times(1)).createPayment(any(Payment.class));
    }

    @Test
    void normalizedReplayKeepsReceiptButChangedMetadataIsAConflict() throws Exception {
        String key = UUID.randomUUID().toString();
        MvcResult first = create(body("12.50", "USD", "Customer@example.com"), key);
        MvcResult replay = create(body("12.5", "usd", "CUSTOMER@example.com"), key);
        assertThat(first.getResponse().getStatus()).isEqualTo(201);
        assertThat(replay.getResponse().getStatus()).isEqualTo(201);
        assertThat((String) read(replay, "$.id")).isEqualTo(read(first, "$.id"));
        assertThat(create(body("12.5", "USD", "other@example.com"), key).getResponse().getStatus()).isEqualTo(409);
        verify(provider, times(1)).createPayment(any(Payment.class));
    }

    @Test
    void concurrentConfirmationProcessesOnlyOnceAndReplaysTheSameCompletion() throws Exception {
        UUID id = pending();
        List<MvcResult> results = overlap("select id from payments where id = ? for update", id,
                List.of(() -> confirm(id, "pm_success"), () -> confirm(id, "pm_success"),
                        () -> confirm(id, "pm_success"), () -> confirm(id, "pm_success")));
        for (MvcResult result : results) {
            assertThat(result.getResponse().getStatus()).isEqualTo(200);
            assertThat((String) read(result, "$.status")).isEqualTo("COMPLETED");
            assertThat((String) read(result, "$.completedAt")).isNotBlank().isEqualTo(read(results.get(0), "$.completedAt"));
        }
        Payment stored = payments.findById(id).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(stored.getFailureReason()).isNull();
        assertThat(stored.getPaymentMethodId()).isEqualTo("pm_success");
        verify(provider, times(1)).confirmPayment(stored.getProviderPaymentId(), "pm_success");
    }

    @Test
    void parallelDifferentConfirmationMethodsCannotOverwriteTheWinningOutcome() throws Exception {
        UUID id = pending();
        List<MvcResult> results = overlap("select id from payments where id = ? for update", id,
                List.of(() -> confirm(id, "pm_success"), () -> confirm(id, "pm_decline")));
        assertThat(results.stream().map(r -> r.getResponse().getStatus()).toList()).containsExactlyInAnyOrder(200, 409);
        int winner = results.get(0).getResponse().getStatus() == 200 ? 0 : 1;
        Payment stored = payments.findById(id).orElseThrow();
        assertThat(stored.getPaymentMethodId()).isEqualTo(winner == 0 ? "pm_success" : "pm_decline");
        assertThat(stored.getStatus()).isEqualTo(winner == 0 ? PaymentStatus.COMPLETED : PaymentStatus.FAILED);
        assertThat((String) read(results.get(winner), "$.status")).isEqualTo(stored.getStatus().name());
        verify(provider, times(1)).confirmPayment(anyString(), anyString());
    }

    @Test
    void aDeclinedPaymentReplaysItsFailedReceiptWithoutAnotherProviderCall() throws Exception {
        UUID id = pending();
        MvcResult first = confirm(id, "pm_decline");
        MvcResult replay = confirm(id, "pm_decline");
        assertThat(first.getResponse().getStatus()).isEqualTo(200);
        assertThat(replay.getResponse().getStatus()).isEqualTo(200);
        assertThat((String) read(replay, "$.status")).isEqualTo("FAILED");
        assertThat((String) read(replay, "$.failureReason")).isEqualTo(read(first, "$.failureReason"));
        assertThat((Object) read(replay, "$.completedAt")).isNull();
        assertThat(confirm(id, "pm_success").getResponse().getStatus()).isEqualTo(409);
        verify(provider, times(1)).confirmPayment(anyString(), anyString());
    }

    @Test
    void unavailableInitializationRollsBackAndDoesNotConsumeTheKey() throws Exception {
        String key = UUID.randomUUID().toString();
        doThrow(new ProviderUnavailableException("private provider detail")).doCallRealMethod()
                .when(provider).createPayment(any(Payment.class));
        MvcResult failed = create(body("12.50", "USD", "customer@example.com"), key);
        assertThat(failed.getResponse().getStatus()).isEqualTo(503);
        assertThat((String) read(failed, "$.code")).isEqualTo("provider_unavailable");
        assertThat(failed.getResponse().getContentAsString()).doesNotContain("private provider detail");
        assertThat(payments.findByIdempotencyKey(key)).isEmpty();
        assertThat(create(body("12.50", "USD", "customer@example.com"), key).getResponse().getStatus()).isEqualTo(201);
        verify(provider, times(2)).createPayment(any(Payment.class));
    }

    @Test
    void unavailableConfirmationLeavesPendingAndSameMethodCanRetryAfterRecovery() throws Exception {
        UUID id = pending();
        doThrow(new ProviderUnavailableException("private provider detail")).doCallRealMethod()
                .when(provider).confirmPayment(anyString(), anyString());
        MvcResult unavailable = confirm(id, "pm_success");
        assertThat(unavailable.getResponse().getStatus()).isEqualTo(503);
        assertThat(unavailable.getResponse().getContentAsString()).doesNotContain("private provider detail");
        Payment stored = payments.findById(id).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(stored.getPaymentMethodId()).isNull();
        assertThat(stored.getCompletedAt()).isNull();
        assertThat(stored.getFailureReason()).isNull();
        MvcResult recovered = confirm(id, "pm_success");
        assertThat(recovered.getResponse().getStatus()).isEqualTo(200);
        assertThat((String) read(recovered, "$.status")).isEqualTo("COMPLETED");
        assertThat(confirm(id, "pm_success").getResponse().getStatus()).isEqualTo(200);
        verify(provider, times(2)).confirmPayment(anyString(), anyString());
    }

    @Test
    void missingPaymentAndMalformedIdentifiersHaveUsefulStatusCodes() throws Exception {
        assertThat(mvc.perform(get(API + "/" + UUID.randomUUID())).andReturn().getResponse().getStatus()).isEqualTo(404);
        assertThat(mvc.perform(get(API + "/not-a-uuid")).andReturn().getResponse().getStatus()).isEqualTo(400);
    }

    private UUID pending() throws Exception {
        MvcResult created = create(body("12.50", "USD", "customer@example.com"), UUID.randomUUID().toString());
        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        return UUID.fromString(read(created, "$.id"));
    }

    private MvcResult create(String body, String key) throws Exception {
        return mvc.perform(post(API).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
    }

    private MvcResult confirm(UUID id, String method) throws Exception {
        return mvc.perform(post(API + "/" + id + "/confirm").contentType(MediaType.APPLICATION_JSON)
                .content("{\"paymentMethodId\":\"" + method + "\"}")).andReturn();
    }

    private static String body(String amount, String currency, String email) {
        return """
                {"amount":%s,"currency":"%s","customerEmail":"%s","customerId":"customer-1","description":"Demo payment"}
                """.formatted(amount, currency, email);
    }

    private List<MvcResult> overlap(String heldSql, Object parameter, List<Callable<MvcResult>> requests) throws Exception {
        var pool = Executors.newFixedThreadPool(requests.size());
        try (Connection holder = dataSource.getConnection()) {
            holder.setAutoCommit(false);
            try (var statement = holder.prepareStatement(heldSql)) {
                if (parameter != null) statement.setObject(1, parameter);
                if (statement.execute()) statement.getResultSet().close();
            }
            try {
                CountDownLatch start = new CountDownLatch(1);
                List<Future<MvcResult>> futures = requests.stream().map(request -> pool.submit(() -> {
                    if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Concurrent start timed out");
                    return request.call();
                })).toList();
                start.countDown();
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                int blocked = 0;
                while (System.nanoTime() < deadline) {
                    blocked = jdbc.queryForObject("""
                            select count(*) from pg_stat_activity
                            where datname = current_database() and wait_event_type = 'Lock'
                            and pid <> pg_backend_pid()
                            """, Integer.class);
                    if (blocked == requests.size()) break;
                    Thread.sleep(25);
                }
                assertThat(blocked).as("Every request must overlap at a PostgreSQL lock").isEqualTo(requests.size());
                holder.commit();
                List<MvcResult> results = new ArrayList<>();
                for (Future<MvcResult> future : futures) results.add(future.get(10, TimeUnit.SECONDS));
                return results;
            } finally {
                holder.rollback();
            }
        } finally {
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static <T> T read(MvcResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), path);
    }
}
