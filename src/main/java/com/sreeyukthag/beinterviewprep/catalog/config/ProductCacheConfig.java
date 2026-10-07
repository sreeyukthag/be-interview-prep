package com.sreeyukthag.beinterviewprep.catalog.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Cache advice wraps the transaction advice: a cache hit never opens a transaction or borrows a connection,
 * and an eviction runs only after the write has committed, so a concurrent miss cannot re-cache the old row
 * after the eviction.
 */
@Configuration
@EnableCaching(order = Ordered.HIGHEST_PRECEDENCE)
public class ProductCacheConfig {

    public static final String PRODUCTS_CACHE = "products";
}
