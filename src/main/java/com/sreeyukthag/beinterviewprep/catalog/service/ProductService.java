package com.sreeyukthag.beinterviewprep.catalog.service;

import com.sreeyukthag.beinterviewprep.catalog.config.ProductCacheConfig;
import com.sreeyukthag.beinterviewprep.catalog.dto.request.ProductFilter;
import com.sreeyukthag.beinterviewprep.catalog.dto.request.ProductRequest;
import com.sreeyukthag.beinterviewprep.catalog.dto.response.ProductResponse;
import com.sreeyukthag.beinterviewprep.catalog.entity.Product;
import com.sreeyukthag.beinterviewprep.catalog.mapper.ProductMapper;
import com.sreeyukthag.beinterviewprep.catalog.repository.ProductRepository;
import com.sreeyukthag.beinterviewprep.catalog.repository.ProductSpecifications;
import com.sreeyukthag.beinterviewprep.common.exception.ResourceNotFoundException;
import com.sreeyukthag.beinterviewprep.common.web.PageResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;

    public PageResponse<ProductResponse> search(ProductFilter filter, Pageable pageable) {
        return PageResponse.of(
                productRepository.findAll(ProductSpecifications.matching(filter), pageable), ProductMapper::toResponse);
    }

    @Cacheable(cacheNames = ProductCacheConfig.PRODUCTS_CACHE, key = "#id", sync = true)
    public ProductResponse get(UUID id) {
        return ProductMapper.toResponse(find(id));
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        Product product = new Product();
        ProductMapper.apply(request, product);
        return ProductMapper.toResponse(productRepository.saveAndFlush(product));
    }

    @Transactional
    @CacheEvict(cacheNames = ProductCacheConfig.PRODUCTS_CACHE, key = "#id")
    public ProductResponse update(UUID id, ProductRequest request) {
        Product product = find(id);
        ProductMapper.apply(request, product);
        return ProductMapper.toResponse(productRepository.saveAndFlush(product));
    }

    @Transactional
    @CacheEvict(cacheNames = ProductCacheConfig.PRODUCTS_CACHE, key = "#id")
    public void delete(UUID id) {
        productRepository.delete(find(id));
    }

    private Product find(UUID id) {
        return productRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Product", id));
    }
}
