package com.cmanzano.orders.bdd.fakes;

import com.cmanzano.orders.application.port.out.ProductCatalogPort;
import com.cmanzano.orders.domain.model.Product;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class InMemoryProductCatalog implements ProductCatalogPort {

    private final Map<String, Product> products = new HashMap<>();

    public void add(Product product) {
        products.put(product.sku(), product);
    }

    @Override
    public Flux<Product> findBySkus(Set<String> skus) {
        return Flux.fromIterable(skus).filter(products::containsKey).map(products::get);
    }
}
