package com.cmanzano.orders.infrastructure.aop;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Programación orientada a aspectos: mide duración y resultado de cada caso de uso sin
 * contaminar la capa de aplicación con código de logging/métricas (concern transversal).
 * <p>
 * Como los casos de uso son reactivos, la medición se engancha a la suscripción del
 * Mono/Flux y no a la invocación del método (que solo ensambla el pipeline).
 */
@Aspect
@Component
public class UseCaseObservabilityAspect {

    private static final Logger log = LoggerFactory.getLogger(UseCaseObservabilityAspect.class);

    private final MeterRegistry meterRegistry;

    public UseCaseObservabilityAspect(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Around("execution(public * com.cmanzano.orders.application.service..*(..))")
    public Object observe(ProceedingJoinPoint joinPoint) throws Throwable {
        String useCase = joinPoint.getSignature().getDeclaringType().getSimpleName() + "."
                + joinPoint.getSignature().getName();
        Object result = joinPoint.proceed();

        if (result instanceof Mono<?> mono) {
            return Mono.defer(() -> {
                long start = System.nanoTime();
                return mono.doOnSuccess(value -> record(useCase, start, "success"))
                        .doOnError(error -> record(useCase, start, error.getClass().getSimpleName()));
            });
        }
        if (result instanceof Flux<?> flux) {
            return Flux.defer(() -> {
                long start = System.nanoTime();
                return flux.doOnComplete(() -> record(useCase, start, "success"))
                        .doOnError(error -> record(useCase, start, error.getClass().getSimpleName()));
            });
        }
        return result;
    }

    private void record(String useCase, long startNanos, String outcome) {
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startNanos);
        Timer.builder("orders.usecase")
                .description("Use case execution time")
                .tag("usecase", useCase)
                .tag("outcome", outcome)
                .register(meterRegistry)
                .record(elapsed);
        log.info("use_case={} outcome={} duration_ms={}", useCase, outcome, elapsed.toMillis());
    }
}
