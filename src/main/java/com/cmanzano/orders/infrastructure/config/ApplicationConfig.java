package com.cmanzano.orders.infrastructure.config;

import com.cmanzano.orders.application.port.out.DomainEventPublisherPort;
import com.cmanzano.orders.application.port.out.OrderAuditRepositoryPort;
import com.cmanzano.orders.application.port.out.OrderRepositoryPort;
import com.cmanzano.orders.application.port.out.PaymentGatewayPort;
import com.cmanzano.orders.application.port.out.ProductCatalogPort;
import com.cmanzano.orders.application.service.OrderApplicationService;
import com.cmanzano.orders.application.service.OrderAuditService;
import com.cmanzano.orders.domain.policy.DiscountPolicies;
import com.cmanzano.orders.domain.policy.DiscountPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Composition root: aquí se "enchufan" los adaptadores a los puertos. Las capas de dominio y
 * aplicación no conocen Spring; solo esta configuración sabe qué implementación usar.
 */
@Configuration
public class ApplicationConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    DiscountPolicy discountPolicy() {
        return DiscountPolicies.standard();
    }

    @Bean
    OrderApplicationService orderApplicationService(OrderRepositoryPort orderRepository,
                                                    ProductCatalogPort productCatalog,
                                                    PaymentGatewayPort paymentGateway,
                                                    DomainEventPublisherPort eventPublisher,
                                                    DiscountPolicy discountPolicy,
                                                    Clock clock) {
        return new OrderApplicationService(orderRepository, productCatalog, paymentGateway, eventPublisher,
                discountPolicy, clock);
    }

    @Bean
    OrderAuditService orderAuditService(OrderAuditRepositoryPort auditRepository) {
        return new OrderAuditService(auditRepository);
    }
}
