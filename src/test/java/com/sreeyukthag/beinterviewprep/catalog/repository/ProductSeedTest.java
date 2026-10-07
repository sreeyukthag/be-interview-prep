package com.sreeyukthag.beinterviewprep.catalog.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.sreeyukthag.beinterviewprep.catalog.entity.Product;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ProductSeedTest {

    @Autowired
    private ProductRepository productRepository;

    @Test
    void liquibaseSeedsOneHundredProducts() {
        List<Product> products = productRepository.findAll();

        assertThat(products).hasSize(100);
        assertThat(products).extracting(Product::getCategory).contains("electronics", "books", "toys");
        assertThat(products).anyMatch(product -> product.getStock() == 0);
        assertThat(products).allMatch(product -> product.getCreatedAt() != null);
    }
}
