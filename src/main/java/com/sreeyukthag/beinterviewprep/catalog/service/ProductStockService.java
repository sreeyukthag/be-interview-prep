package com.sreeyukthag.beinterviewprep.catalog.service;

import com.sreeyukthag.beinterviewprep.catalog.config.ProductCacheConfig;
import com.sreeyukthag.beinterviewprep.catalog.repository.ProductRepository;
import com.sreeyukthag.beinterviewprep.catalog.repository.ProductStockSnapshot;
import com.sreeyukthag.beinterviewprep.catalog.stock.InsufficientStockException;
import com.sreeyukthag.beinterviewprep.catalog.stock.ReservedItem;
import com.sreeyukthag.beinterviewprep.catalog.stock.StockService;
import com.sreeyukthag.beinterviewprep.common.exception.ResourceNotFoundException;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
class ProductStockService implements StockService {

    private final ProductRepository productRepository;
    private final CacheManager cacheManager;

    @Override
    public ReservedItem reserve(UUID productId, int quantity) {
        if (productRepository.decrementStock(productId, quantity) == 0) {
            ProductStockSnapshot product = snapshot(productId);
            throw new InsufficientStockException(productId, product.getName(), quantity, product.getStock());
        }
        evictAfterCommit(productId);
        return new ReservedItem(productId, quantity, snapshot(productId).getPriceCents());
    }

    @Override
    public void release(UUID productId, int quantity) {
        if (productRepository.incrementStock(productId, quantity) == 0) {
            log.warn("Product {} no longer exists; {} released units were dropped", productId, quantity);
            return;
        }
        evictAfterCommit(productId);
    }

    private ProductStockSnapshot snapshot(UUID productId) {
        return productRepository
                .findStockSnapshotById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));
    }

    /**
     * The bulk UPDATE bypasses the entity, so no cache annotation sees it. Evicting after commit means a rolled-back
     * reservation leaves the cache alone, and a lookup that ran before the commit cannot leave the old stock cached.
     */
    private void evictAfterCommit(UUID productId) {
        Cache cache = Objects.requireNonNull(cacheManager.getCache(ProductCacheConfig.PRODUCTS_CACHE));
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                cache.evict(productId);
            }
        });
    }
}
