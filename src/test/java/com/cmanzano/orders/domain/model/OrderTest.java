package com.cmanzano.orders.domain.model;

import com.cmanzano.orders.domain.event.OrderCancelled;
import com.cmanzano.orders.domain.event.OrderCreated;
import com.cmanzano.orders.domain.event.OrderPaid;
import com.cmanzano.orders.domain.exception.InvalidOrderException;
import com.cmanzano.orders.domain.exception.InvalidOrderStateException;
import com.cmanzano.orders.domain.policy.DiscountPolicies;
import com.cmanzano.orders.domain.policy.DiscountPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-19T10:00:00Z"), ZoneOffset.UTC);
    private static final CustomerId ALICE = new CustomerId("alice");

    private static OrderItem item(String sku, int quantity, String price) {
        return new OrderItem(sku, sku, quantity, Money.of(price, "USD"));
    }

    private static Order newOrder() {
        return Order.create(ALICE, List.of(item("MOUSE-01", 2, "25.00")), DiscountPolicy.none(), CLOCK);
    }

    @Nested
    @DisplayName("Al crear un pedido")
    class Creation {

        @Test
        void calculatesTotalsAndStartsInCreatedStatus() {
            Order order = Order.create(ALICE,
                    List.of(item("MOUSE-01", 2, "25.00"), item("KEYBOARD-01", 1, "45.00")),
                    DiscountPolicy.none(), CLOCK);

            assertThat(order.status()).isEqualTo(OrderStatus.CREATED);
            assertThat(order.subtotal()).isEqualTo(Money.of("95.00", "USD"));
            assertThat(order.discount()).isEqualTo(Money.zero("USD"));
            assertThat(order.total()).isEqualTo(Money.of("95.00", "USD"));
            assertThat(order.createdAt()).isEqualTo(CLOCK.instant());
            assertThat(order.version()).isNull();
        }

        @Test
        void appliesTheDiscountPolicy() {
            Order order = Order.create(ALICE, List.of(item("MOUSE-01", 10, "25.00")), DiscountPolicies.standard(), CLOCK);

            assertThat(order.discount()).isEqualTo(Money.of("25.00", "USD"));
            assertThat(order.total()).isEqualTo(Money.of("225.00", "USD"));
        }

        @Test
        void neverDiscountsMoreThanTheSubtotal() {
            DiscountPolicy abusive = (subtotal, items) -> Money.of("999.00", "USD");

            Order order = Order.create(ALICE, List.of(item("MOUSE-01", 1, "25.00")), abusive, CLOCK);

            assertThat(order.total()).isEqualTo(Money.zero("USD"));
        }

        @Test
        void recordsAnOrderCreatedEventOnlyOnce() {
            Order order = newOrder();

            assertThat(order.pullDomainEvents()).singleElement().isInstanceOf(OrderCreated.class);
            assertThat(order.pullDomainEvents()).isEmpty();
        }

        @Test
        void requiresAtLeastOneItem() {
            assertThatThrownBy(() -> Order.create(ALICE, List.of(), DiscountPolicy.none(), CLOCK))
                    .isInstanceOf(InvalidOrderException.class);
        }

        @Test
        void exposesAnImmutableListOfItems() {
            Order order = newOrder();

            assertThatThrownBy(() -> order.items().add(item("X-01", 1, "1.00")))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    @DisplayName("Transiciones de estado")
    class StateTransitions {

        @Test
        void createdOrderCanBePaid() {
            Order order = newOrder();
            order.pullDomainEvents();

            order.markAsPaid("PAY-123", CLOCK);

            assertThat(order.status()).isEqualTo(OrderStatus.PAID);
            assertThat(order.paymentReference()).isEqualTo("PAY-123");
            assertThat(order.pullDomainEvents()).singleElement().isInstanceOf(OrderPaid.class);
        }

        @Test
        void createdOrderCanBeCancelled() {
            Order order = newOrder();
            order.pullDomainEvents();

            order.cancel("Changed my mind", CLOCK);

            assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(order.pullDomainEvents()).singleElement().isInstanceOf(OrderCancelled.class);
        }

        @Test
        void paidOrderCannotBeCancelled() {
            Order order = newOrder();
            order.markAsPaid("PAY-123", CLOCK);

            assertThatThrownBy(() -> order.cancel("Too late", CLOCK))
                    .isInstanceOf(InvalidOrderStateException.class)
                    .hasMessageContaining("PAID to CANCELLED");
        }

        @Test
        void cancelledOrderCannotBePaid() {
            Order order = newOrder();
            order.cancel("Changed my mind", CLOCK);

            assertThatThrownBy(order::ensurePayable).isInstanceOf(InvalidOrderStateException.class);
        }
    }
}
