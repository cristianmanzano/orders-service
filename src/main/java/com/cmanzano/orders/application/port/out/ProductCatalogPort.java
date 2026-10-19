package com.cmanzano.orders.application.port.out;

import com.cmanzano.orders.domain.model.Product;
import reactor.core.publisher.Flux;

import java.util.Set;

public interface ProductCatalogPort {

    /**
     * Devuelve solo los productos activos cuyo SKU esté en el conjunto recibido.
     */
    Flux<Product> findBySkus(Set<String> skus);
}
