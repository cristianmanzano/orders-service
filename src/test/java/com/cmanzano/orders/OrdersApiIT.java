package com.cmanzano.orders;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Prueba end-to-end: levanta la aplicación completa contra infraestructura real en contenedores
 * (PostgreSQL, Kafka, DynamoDB Local) y simula el proveedor de pagos con WireMock.
 */
@Testcontainers
@ActiveProfiles("local")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrdersApiIT {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static final KafkaContainer kafka = new KafkaContainer("apache/kafka:3.9.1");

    @Container
    static final GenericContainer<?> dynamodb = new GenericContainer<>("amazon/dynamodb-local:2.6.1")
            .withCommand("-jar DynamoDBLocal.jar -inMemory -sharedDb")
            .withExposedPorts(8000);

    static final WireMockServer paymentProvider = new WireMockServer(wireMockConfig().dynamicPort());

    static {
        paymentProvider.start();
    }

    @DynamicPropertySource
    static void infrastructureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", () -> "r2dbc:postgresql://%s:%d/%s".formatted(
                postgres.getHost(), postgres.getMappedPort(5432), postgres.getDatabaseName()));
        registry.add("spring.r2dbc.username", postgres::getUsername);
        registry.add("spring.r2dbc.password", postgres::getPassword);
        registry.add("spring.flyway.url", postgres::getJdbcUrl);
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("app.aws.dynamodb.endpoint",
                () -> "http://%s:%d".formatted(dynamodb.getHost(), dynamodb.getMappedPort(8000)));
        registry.add("app.aws.dynamodb.create-table", () -> "true");
        registry.add("app.payment.base-url", paymentProvider::baseUrl);
    }

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @AfterAll
    static void stopPaymentProvider() {
        paymentProvider.stop();
    }

    @BeforeEach
    void setUp() {
        webTestClient = webTestClient.mutate().responseTimeout(Duration.ofSeconds(30)).build();
        paymentProvider.resetAll();
        circuitBreakerRegistry.circuitBreaker("paymentGateway").reset();
    }

    @Test
    void rejectsRequestsWithoutAToken() {
        webTestClient.get().uri("/api/v1/orders")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void rejectsInvalidCredentials() {
        webTestClient.post().uri("/api/v1/auth/token")
                .bodyValue(Map.of("username", "alice", "password", "wrong-password"))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void completesTheOrderLifecycleAndBuildsTheAuditTrailThroughKafka() {
        paymentProvider.stubFor(post("/v1/charges").willReturn(okJson("""
                {"status": "APPROVED", "reference": "PAY-IT-001"}""")));
        String alice = tokenFor("alice", "alice123");

        JsonNode created = webTestClient.post().uri("/api/v1/orders")
                .headers(h -> h.setBearerAuth(alice))
                .bodyValue(Map.of("items", List.of(Map.of("sku", "MOUSE-01", "quantity", 2))))
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().exists(HttpHeaders.LOCATION)
                .expectBody(JsonNode.class).returnResult().getResponseBody();

        assertThat(created).isNotNull();
        assertThat(created.get("status").asText()).isEqualTo("CREATED");
        assertThat(created.get("total").decimalValue()).isEqualByComparingTo("50.00");
        assertThat(created.get("customerId").asText()).isEqualTo("alice");
        String orderId = created.get("id").asText();

        webTestClient.post().uri("/api/v1/orders/{id}/payment", orderId)
                .headers(h -> h.setBearerAuth(alice))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("PAID")
                .jsonPath("$.paymentReference").isEqualTo("PAY-IT-001");

        String admin = tokenFor("admin", "admin123");
        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(500)).untilAsserted(() -> {
            List<String> eventTypes = webTestClient.get().uri("/api/v1/orders/{id}/audit", orderId)
                    .headers(h -> h.setBearerAuth(admin))
                    .exchange()
                    .expectStatus().isOk()
                    .expectBodyList(JsonNode.class).returnResult().getResponseBody()
                    .stream().map(entry -> entry.get("eventType").asText()).toList();
            assertThat(eventTypes).containsExactly("ORDER_CREATED", "ORDER_PAID");
        });
    }

    @Test
    void customersCannotAccessOrdersFromOtherCustomers() {
        String orderId = createOrderAs(tokenFor("alice", "alice123"));

        webTestClient.get().uri("/api/v1/orders/{id}", orderId)
                .headers(h -> h.setBearerAuth(tokenFor("bob", "bob123")))
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void onlyAdminsCanReadTheAuditTrail() {
        String alice = tokenFor("alice", "alice123");
        String orderId = createOrderAs(alice);

        webTestClient.get().uri("/api/v1/orders/{id}/audit", orderId)
                .headers(h -> h.setBearerAuth(alice))
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void validatesTheRequestPayload() {
        webTestClient.post().uri("/api/v1/orders")
                .headers(h -> h.setBearerAuth(tokenFor("alice", "alice123")))
                .bodyValue(Map.of("items", List.of(Map.of("sku", "bad sku!", "quantity", 0))))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.title").isEqualTo("Validation failed")
                .jsonPath("$.errors.length()").isEqualTo(2);
    }

    @Test
    void doesNotAcceptInactiveProducts() {
        webTestClient.post().uri("/api/v1/orders")
                .headers(h -> h.setBearerAuth(tokenFor("alice", "alice123")))
                .bodyValue(Map.of("items", List.of(Map.of("sku", "WEBCAM-HD", "quantity", 1))))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
                .expectBody()
                .jsonPath("$.detail").isEqualTo("Unknown product: WEBCAM-HD");
    }

    @Test
    void returnsServiceUnavailableWhenThePaymentProviderIsDownAndKeepsTheOrderUnpaid() {
        paymentProvider.stubFor(post("/v1/charges").willReturn(aResponse().withStatus(500)));
        String alice = tokenFor("alice", "alice123");
        String orderId = createOrderAs(alice);

        webTestClient.post().uri("/api/v1/orders/{id}/payment", orderId)
                .headers(h -> h.setBearerAuth(alice))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);

        webTestClient.get().uri("/api/v1/orders/{id}", orderId)
                .headers(h -> h.setBearerAuth(alice))
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("CREATED");
    }

    @Test
    void paidOrdersCannotBeCancelled() {
        paymentProvider.stubFor(post("/v1/charges").willReturn(okJson("""
                {"status": "APPROVED", "reference": "PAY-IT-002"}""")));
        String alice = tokenFor("alice", "alice123");
        String orderId = createOrderAs(alice);
        webTestClient.post().uri("/api/v1/orders/{id}/payment", orderId)
                .headers(h -> h.setBearerAuth(alice))
                .exchange()
                .expectStatus().isOk();

        webTestClient.post().uri("/api/v1/orders/{id}/cancellation", orderId)
                .headers(h -> h.setBearerAuth(alice))
                .bodyValue(Map.of("reason", "Too late"))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT);
    }

    private String createOrderAs(String token) {
        JsonNode body = webTestClient.post().uri("/api/v1/orders")
                .headers(h -> h.setBearerAuth(token))
                .bodyValue(Map.of("items", List.of(Map.of("sku", "KEYBOARD-01", "quantity", 1))))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(JsonNode.class).returnResult().getResponseBody();
        assertThat(body).isNotNull();
        return body.get("id").asText();
    }

    private String tokenFor(String username, String password) {
        JsonNode body = webTestClient.post().uri("/api/v1/auth/token")
                .bodyValue(Map.of("username", username, "password", password))
                .exchange()
                .expectStatus().isOk()
                .expectBody(JsonNode.class).returnResult().getResponseBody();
        assertThat(body).isNotNull();
        return body.get("accessToken").asText();
    }
}
