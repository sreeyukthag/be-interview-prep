package com.sreeyukthag.beinterviewprep.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.benmanes.caffeine.cache.Cache;
import com.sreeyukthag.beinterviewprep.catalog.config.ProductCacheConfig;
import com.sreeyukthag.beinterviewprep.catalog.dto.request.ProductRequest;
import com.sreeyukthag.beinterviewprep.catalog.dto.response.ProductResponse;
import com.sreeyukthag.beinterviewprep.catalog.repository.ProductRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(roles = "ADMIN")
class ProductCachingTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private MockMvc mockMvc;

    @MockitoSpyBean
    private ProductRepository productRepository;

    @Test
    void repeatedLookupsHitTheDatabaseOnce() {
        UUID id = productService.create(request("Desk Lamp", 4_999)).id();
        long hitsBefore = nativeCache().stats().hitCount();

        ProductResponse first = productService.get(id);
        ProductResponse second = productService.get(id);
        ProductResponse third = productService.get(id);

        verify(productRepository, times(1)).findById(id);
        assertThat(second).isEqualTo(first).isEqualTo(third);
        assertThat(nativeCache().stats().hitCount() - hitsBefore).isEqualTo(2);
    }

    @Test
    void lookupAfterUpdateReturnsTheNewValue() {
        UUID id = productService.create(request("Desk Lamp", 4_999)).id();
        productService.get(id);

        productService.update(id, request("Desk Lamp Pro", 7_999));
        ProductResponse afterUpdate = productService.get(id);

        assertThat(afterUpdate.name()).isEqualTo("Desk Lamp Pro");
        assertThat(afterUpdate.priceCents()).isEqualTo(7_999);
    }

    @Test
    void lookupAfterDeleteReturns404() throws Exception {
        UUID id = productService.create(request("Desk Lamp", 4_999)).id();
        productService.get(id);

        productService.delete(id);

        assertThat(nativeCache().getIfPresent(id)).isNull();
        mockMvc.perform(get("/api/v1/products/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
    }

    @SuppressWarnings("unchecked")
    private Cache<Object, Object> nativeCache() {
        return (Cache<Object, Object>)
                cacheManager.getCache(ProductCacheConfig.PRODUCTS_CACHE).getNativeCache();
    }

    private static ProductRequest request(String name, long priceCents) {
        return new ProductRequest(name, "home", priceCents, 5, 4.2);
    }
}
