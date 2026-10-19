package com.cmanzano.orders.infrastructure.adapter.out.payment;

import com.cmanzano.orders.application.exception.PaymentGatewayUnavailableException;
import com.cmanzano.orders.application.port.out.PaymentGatewayPort;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.reactor.bulkhead.operator.BulkheadOperator;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.retry.RetryOperator;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

/**
 * Cliente HTTP reactivo hacia el proveedor de pagos, protegido con patrones de tolerancia a fallos.
 * <p>
 * Orden de los decoradores (de adentro hacia afuera), el recomendado por Resilience4j:
 * <pre>Retry( CircuitBreaker( Timeout( Bulkhead( llamada HTTP ) ) ) )</pre>
 * <ul>
 *   <li><b>Bulkhead</b>: limita las llamadas concurrentes para que un proveedor lento no agote recursos.</li>
 *   <li><b>Timeout</b>: corta llamadas que exceden el tiempo máximo.</li>
 *   <li><b>Circuit Breaker</b>: si la tasa de fallos supera el umbral, deja de llamar (fail-fast).</li>
 *   <li><b>Retry</b>: reintenta fallos transitorios con backoff exponencial.</li>
 * </ul>
 * El header {@code Idempotency-Key} garantiza que un reintento no genere un doble cobro.
 */
@Component
public class HttpPaymentGatewayAdapter implements PaymentGatewayPort {

    static final String RESILIENCE_INSTANCE = "paymentGateway";

    private final WebClient webClient;
    private final PaymentGatewayProperties properties;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    private final Bulkhead bulkhead;

    public HttpPaymentGatewayAdapter(WebClient.Builder webClientBuilder,
                                     PaymentGatewayProperties properties,
                                     CircuitBreakerRegistry circuitBreakerRegistry,
                                     RetryRegistry retryRegistry,
                                     BulkheadRegistry bulkheadRegistry) {
        this.webClient = webClientBuilder.baseUrl(properties.baseUrl()).build();
        this.properties = properties;
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker(RESILIENCE_INSTANCE);
        this.retry = retryRegistry.retry(RESILIENCE_INSTANCE);
        this.bulkhead = bulkheadRegistry.bulkhead(RESILIENCE_INSTANCE);
    }

    @Override
    public Mono<PaymentResult> charge(PaymentRequest request) {
        String idempotencyKey = request.orderId().toString();
        return webClient.post()
                .uri("/v1/charges")
                .header("Idempotency-Key", idempotencyKey)
                .bodyValue(new ChargeRequest(idempotencyKey, request.amount().amount(), request.amount().currency()))
                .retrieve()
                .onStatus(HttpStatusCode::is5xxServerError,
                        response -> Mono.error(new PaymentProviderServerException(response.statusCode().value())))
                .bodyToMono(ChargeResponse.class)
                .map(ChargeResponse::toResult)
                .transformDeferred(BulkheadOperator.of(bulkhead))
                .timeout(properties.timeout())
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .transformDeferred(RetryOperator.of(retry))
                .onErrorMap(ex -> new PaymentGatewayUnavailableException("Payment provider is unavailable", ex));
    }

    public record ChargeRequest(String orderId, BigDecimal amount, String currency) {
    }

    public record ChargeResponse(String status, String reference, String reason) {

        PaymentResult toResult() {
            return "APPROVED".equalsIgnoreCase(status)
                    ? PaymentResult.ofApproved(reference)
                    : PaymentResult.ofRejected(reason != null ? reason : "Rejected by provider");
        }
    }
}
