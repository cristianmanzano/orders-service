package com.cmanzano.orders.infrastructure.adapter.out.persistence.postgres;

import com.cmanzano.orders.application.port.out.ProductCatalogPort;
import com.cmanzano.orders.domain.model.Product;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Set;

@Component
public class PostgresProductCatalogAdapter implements ProductCatalogPort {

    private final SpringDataProductRepository products;

    PostgresProductCatalogAdapter(SpringDataProductRepository products) {
        this.products = products;
    }

    @Override
    public Flux<Product> findBySkus(Set<String> skus) {
        if (skus.isEmpty()) {
            return Flux.empty();
        }
        return products.findAllById(skus)
                .filter(ProductEntity::isActive)
                .map(OrderPersistenceMapper::toDomain);
    }
}
