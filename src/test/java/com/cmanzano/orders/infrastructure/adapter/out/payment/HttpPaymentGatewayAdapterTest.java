package com.cmanzano.orders.infrastructure.adapter.out.payment;

import com.cmanzano.orders.application.exception.PaymentGatewayUnavailableException;
import com.cmanzano.orders.application.port.out.PaymentGatewayPort.PaymentRequest;
import com.cmanzano.orders.domain.model.Money;
import com.cmanzano.orders.domain.model.OrderId;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba el adaptador contra un servidor HTTP simulado (WireMock) con las políticas de
 * Resilience4j reales, sin levantar el contexto de Spring.
 */
class HttpPaymentGatewayAdapterTest {

    private static final WireMockServer provider = new WireMockServer(wireMockConfig().dynamicPort());
    private static final PaymentRequest REQUEST = new PaymentRequest(OrderId.newId(), Money.of("50.00", "USD"));

    private CircuitBreakerRegistry circuitBreakerRegistry;
    private HttpPaymentGatewayAdapter adapter;

    @BeforeAll
    static void startProvider() {
        provider.start();
    }

    @AfterAll
    static void stopProvider() {
        provider.stop();
    }

    @BeforeEach
    void setUp() {
        provider.resetAll();
        var transientFailures = new Class[]{PaymentProviderServerException.class, TimeoutException.class,
                WebClientRequestException.class};

        circuitBreakerRegistry = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
                .slidingWindowSize(4)
                .minimumNumberOfCalls(4)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofMinutes(1))
                .recordExceptions(transientFailures)
                .build());
        RetryRegistry retryRegistry = RetryRegistry.of(RetryConfig.custom()
                .maxAttempts(2)
                .waitDuration(Duration.ofMillis(10))
                .retryExceptions(transientFailures)
                .build());

        adapter = new HttpPaymentGatewayAdapter(WebClient.builder(),
                new PaymentGatewayProperties(provider.baseUrl(), Duration.ofMillis(500)),
                circuitBreakerRegistry, retryRegistry, BulkheadRegistry.ofDefaults());
    }

    @Test
    void returnsApprovedPaymentsAndSendsAnIdempotencyKey() {
        provider.stubFor(post("/v1/charges").willReturn(okJson("""
                {"status": "APPROVED", "reference": "PAY-42"}""")));

        StepVerifier.create(adapter.charge(REQUEST))
                .assertNext(result -> {
                    assertThat(result.approved()).isTrue();
                    assertThat(result.reference()).isEqualTo("PAY-42");
                })
                .verifyComplete();

        provider.verify(postRequestedFor(urlEqualTo("/v1/charges"))
                .withHeader("Idempotency-Key", equalTo(REQUEST.orderId().toString())));
    }

    @Test
    void mapsBusinessRejections() {
        provider.stubFor(post("/v1/charges").willReturn(okJson("""
                {"status": "REJECTED", "reason": "Insufficient funds"}""")));

        StepVerifier.create(adapter.charge(REQUEST))
                .assertNext(result -> {
                    assertThat(result.approved()).isFalse();
                    assertThat(result.reason()).isEqualTo("Insufficient funds");
                })
                .verifyComplete();
    }

    @Test
    void retriesTransientServerErrors() {
        provider.stubFor(post("/v1/charges").inScenario("flaky")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("recovered"));
        provider.stubFor(post("/v1/charges").inScenario("flaky")
                .whenScenarioStateIs("recovered")
                .willReturn(okJson("""
                        {"status": "APPROVED", "reference": "PAY-AFTER-RETRY"}""")));

        StepVerifier.create(adapter.charge(REQUEST))
                .assertNext(result -> assertThat(result.reference()).isEqualTo("PAY-AFTER-RETRY"))
                .verifyComplete();

        provider.verify(2, postRequestedFor(urlEqualTo("/v1/charges")));
    }

    @Test
    void timesOutSlowResponses() {
        provider.stubFor(post("/v1/charges").willReturn(okJson("{\"status\":\"APPROVED\"}").withFixedDelay(2_000)));

        StepVerifier.create(adapter.charge(REQUEST))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(PaymentGatewayUnavailableException.class)
                        .hasCauseInstanceOf(TimeoutException.class))
                .verify();
    }

    @Test
    void opensTheCircuitAfterRepeatedFailuresAndStopsCallingTheProvider() {
        provider.stubFor(post("/v1/charges").willReturn(aResponse().withStatus(500)));

        // 2 llamadas x 2 intentos = 4 fallos registrados -> el circuito se abre
        for (int i = 0; i < 2; i++) {
            StepVerifier.create(adapter.charge(REQUEST)).expectError(PaymentGatewayUnavailableException.class).verify();
        }
        assertThat(circuitBreakerRegistry.circuitBreaker(HttpPaymentGatewayAdapter.RESILIENCE_INSTANCE).getState())
                .isEqualTo(CircuitBreaker.State.OPEN);

        StepVerifier.create(adapter.charge(REQUEST))
                .expectErrorSatisfies(error -> assertThat(error).hasCauseInstanceOf(CallNotPermittedException.class))
                .verify();
        provider.verify(4, postRequestedFor(urlEqualTo("/v1/charges")));
    }
}
