package com.cmanzano.orders.bdd;

import com.cmanzano.orders.application.port.in.CreateOrderUseCase.CreateOrderCommand;
import com.cmanzano.orders.application.port.in.CreateOrderUseCase.OrderLine;
import com.cmanzano.orders.application.port.in.Requester;
import com.cmanzano.orders.application.service.OrderApplicationService;
import com.cmanzano.orders.bdd.fakes.InMemoryOrderRepository;
import com.cmanzano.orders.bdd.fakes.InMemoryProductCatalog;
import com.cmanzano.orders.bdd.fakes.RecordingEventPublisher;
import com.cmanzano.orders.bdd.fakes.StubPaymentGateway;
import com.cmanzano.orders.domain.model.Money;
import com.cmanzano.orders.domain.model.Order;
import com.cmanzano.orders.domain.model.OrderStatus;
import com.cmanzano.orders.domain.model.Product;
import com.cmanzano.orders.domain.policy.DiscountPolicies;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Dado;
import io.cucumber.java.es.Entonces;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Los escenarios ejercitan los casos de uso reales con adaptadores en memoria: validan reglas de
 * negocio sin infraestructura, en milisegundos. Es un beneficio directo de la arquitectura hexagonal.
 */
public class OrderSteps {

    private final InMemoryProductCatalog catalog = new InMemoryProductCatalog();
    private final InMemoryOrderRepository repository = new InMemoryOrderRepository();
    private final RecordingEventPublisher publisher = new RecordingEventPublisher();
    private final StubPaymentGateway paymentGateway = new StubPaymentGateway();
    private final OrderApplicationService service = new OrderApplicationService(
            repository, catalog, paymentGateway, publisher, DiscountPolicies.standard(), Clock.systemUTC());

    private Order currentOrder;
    private Throwable lastError;

    @Dado("que el catálogo tiene los siguientes productos:")
    public void elCatalogoTieneLosProductos(DataTable table) {
        table.asMaps().forEach(row -> catalog.add(
                new Product(row.get("sku"), row.get("nombre"), Money.of(row.get("precio"), "USD"))));
    }

    @Cuando("el cliente {string} crea un pedido con:")
    public void elClienteCreaUnPedido(String customer, DataTable table) {
        List<OrderLine> lines = table.asMaps().stream()
                .map(row -> new OrderLine(row.get("sku"), Integer.parseInt(row.get("cantidad"))))
                .toList();
        execute(service.create(new CreateOrderCommand(customer, lines)));
    }

    @Dado("que el cliente {string} tiene un pedido con {int} unidad(es) de {string}")
    public void elClienteTieneUnPedido(String customer, int quantity, String sku) {
        currentOrder = service.create(new CreateOrderCommand(customer, List.of(new OrderLine(sku, quantity)))).block();
        publisher.clear();
    }

    @Dado("la pasarela de pagos aprueba los cobros")
    public void laPasarelaApruebaLosCobros() {
        paymentGateway.approveCharges();
    }

    @Dado("la pasarela de pagos rechaza los cobros")
    public void laPasarelaRechazaLosCobros() {
        paymentGateway.rejectCharges();
    }

    @Cuando("el cliente {string} paga el pedido")
    public void elClientePagaElPedido(String customer) {
        execute(service.pay(currentOrder.id(), Requester.customer(customer)));
    }

    @Cuando("el cliente {string} consulta el pedido")
    public void elClienteConsultaElPedido(String customer) {
        execute(service.getOrder(currentOrder.id(), Requester.customer(customer)));
    }

    @Entonces("el pedido queda en estado {string}")
    public void elPedidoQuedaEnEstado(String status) {
        Order stored = repository.findById(currentOrder.id()).block();
        assertThat(stored).isNotNull();
        assertThat(stored.status()).isEqualTo(OrderStatus.valueOf(status));
    }

    // {word} en lugar de {bigdecimal}: el locale "es" interpretaría el punto como separador de miles
    @Entonces("el total del pedido es {word} USD")
    public void elTotalDelPedidoEs(String total) {
        assertThat(lastError).isNull();
        assertThat(currentOrder.total()).isEqualTo(Money.of(new BigDecimal(total), "USD"));
    }

    @Entonces("se publica el evento {string}")
    public void sePublicaElEvento(String eventType) {
        assertThat(publisher.published()).extracting(event -> event.getClass().getSimpleName()).contains(eventType);
    }

    @Entonces("no se publica ningún evento")
    public void noSePublicaNingunEvento() {
        assertThat(publisher.published()).isEmpty();
    }

    @Entonces("la operación falla con el error {string}")
    public void laOperacionFallaConElError(String errorType) {
        assertThat(lastError).isNotNull();
        assertThat(lastError.getClass().getSimpleName()).isEqualTo(errorType);
    }

    private void execute(Mono<Order> action) {
        try {
            currentOrder = action.block();
            lastError = null;
        } catch (RuntimeException ex) {
            lastError = ex;
        }
    }
}
