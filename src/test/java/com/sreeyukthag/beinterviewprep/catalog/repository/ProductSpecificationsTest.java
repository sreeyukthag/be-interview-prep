package com.sreeyukthag.beinterviewprep.catalog.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.sreeyukthag.beinterviewprep.catalog.dto.request.ProductFilter;
import com.sreeyukthag.beinterviewprep.catalog.entity.Product;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ProductSpecificationsTest {

    @Autowired
    private ProductRepository productRepository;

    @BeforeEach
    void replaceSeedWithFixtures() {
        productRepository.deleteAllInBatch();
        productRepository.saveAll(List.of(
                product("Red Mug", "home", 1_500, 10),
                product("Blue Mug", "home", 2_500, 0),
                product("Promo 50% Mug", "home", 900, 1),
                product("Desk Lamp", "home", 5_000, 3),
                product("Coffee Book", "books", 2_000, 5)));
    }

    @Test
    void noFiltersReturnsEverything() {
        List<String> names = search(ProductFilter.none());

        assertThat(names).hasSize(5);
    }

    @Test
    void filtersByCategory() {
        ProductFilter filter = new ProductFilter("books", null, null, null, null);

        List<String> names = search(filter);

        assertThat(names).containsExactly("Coffee Book");
    }

    @Test
    void filtersByInclusivePriceRange() {
        ProductFilter filter = new ProductFilter(null, 1_500L, 2_500L, null, null);

        List<String> names = search(filter);

        assertThat(names).containsExactlyInAnyOrder("Red Mug", "Blue Mug", "Coffee Book");
    }

    @Test
    void filtersByMinimumPriceAlone() {
        ProductFilter filter = new ProductFilter(null, 2_500L, null, null, null);

        List<String> names = search(filter);

        assertThat(names).containsExactlyInAnyOrder("Blue Mug", "Desk Lamp");
    }

    @Test
    void inStockOnlyExcludesZeroStock() {
        ProductFilter filter = new ProductFilter(null, null, null, true, null);

        List<String> names = search(filter);

        assertThat(names).hasSize(4).doesNotContain("Blue Mug");
    }

    @Test
    void inStockFalseDoesNotFilter() {
        ProductFilter filter = new ProductFilter(null, null, null, false, null);

        List<String> names = search(filter);

        assertThat(names).hasSize(5);
    }

    @Test
    void nameSearchIsCaseInsensitiveSubstring() {
        ProductFilter filter = new ProductFilter(null, null, null, null, "MUG");

        List<String> names = search(filter);

        assertThat(names).containsExactlyInAnyOrder("Red Mug", "Blue Mug", "Promo 50% Mug");
    }

    @Test
    void nameSearchTreatsWildcardsLiterally() {
        ProductFilter filter = new ProductFilter(null, null, null, null, "%");

        List<String> names = search(filter);

        assertThat(names).containsExactly("Promo 50% Mug");
    }

    @Test
    void allFiltersCombineWithAnd() {
        ProductFilter filter = new ProductFilter("home", 1_000L, 3_000L, true, "mug");

        List<String> names = search(filter);

        assertThat(names).containsExactly("Red Mug");
    }

    private List<String> search(ProductFilter filter) {
        return productRepository.findAll(ProductSpecifications.matching(filter)).stream()
                .map(Product::getName)
                .toList();
    }

    private static Product product(String name, String category, long priceCents, int stock) {
        Product product = new Product();
        product.setName(name);
        product.setCategory(category);
        product.setPriceCents(priceCents);
        product.setStock(stock);
        product.setRating(4.0);
        return product;
    }
}
