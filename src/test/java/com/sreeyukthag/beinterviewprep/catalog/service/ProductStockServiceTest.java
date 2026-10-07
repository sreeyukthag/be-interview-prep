package com.sreeyukthag.beinterviewprep.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sreeyukthag.beinterviewprep.catalog.dto.request.ProductRequest;
import com.sreeyukthag.beinterviewprep.catalog.repository.ProductRepository;
import com.sreeyukthag.beinterviewprep.catalog.stock.InsufficientStockException;
import com.sreeyukthag.beinterviewprep.catalog.stock.ReservedItem;
import com.sreeyukthag.beinterviewprep.catalog.stock.StockService;
import com.sreeyukthag.beinterviewprep.common.exception.ResourceNotFoundException;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class ProductStockServiceTest {

    @Autowired
    private StockService stockService;

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void reserveTakesStockAndSnapshotsThePrice() {
        UUID id = createProduct(5, 1_250);

        ReservedItem reserved = inTransaction(() -> stockService.reserve(id, 3));

        assertThat(reserved).isEqualTo(new ReservedItem(id, 3, 1_250));
        assertThat(stockOf(id)).isEqualTo(2);
    }

    @Test
    void reserveCanTakeTheLastUnit() {
        UUID id = createProduct(1, 100);

        inTransaction(() -> stockService.reserve(id, 1));

        assertThat(stockOf(id)).isZero();
    }

    @Test
    void reserveMoreThanAvailableThrowsAndLeavesStockUnchanged() {
        UUID id = createProduct(2, 100);

        InsufficientStockException ex =
                assertThrows(InsufficientStockException.class, () -> inTransaction(() -> stockService.reserve(id, 3)));

        assertThat(ex.getErrorCode()).isEqualTo("INSUFFICIENT_STOCK");
        assertThat(ex.getMessage()).contains("Stock Item", "requested 3", "available 2");
        assertThat(stockOf(id)).isEqualTo(2);
    }

    @Test
    void reserveUnknownProductThrowsNotFound() {
        UUID unknown = UUID.randomUUID();

        assertThrows(ResourceNotFoundException.class, () -> inTransaction(() -> stockService.reserve(unknown, 1)));
    }

    @Test
    void earlierReservationsRollBackWithTheCallersTransaction() {
        UUID available = createProduct(5, 100);
        UUID scarce = createProduct(1, 100);

        assertThrows(
                InsufficientStockException.class,
                () -> inTransaction(() -> {
                    stockService.reserve(available, 2);
                    return stockService.reserve(scarce, 2);
                }));

        assertThat(stockOf(available)).isEqualTo(5);
        assertThat(stockOf(scarce)).isEqualTo(1);
    }

    @Test
    void releasePutsStockBack() {
        UUID id = createProduct(4, 100);

        inTransaction(() -> {
            stockService.release(id, 3);
            return null;
        });

        assertThat(stockOf(id)).isEqualTo(7);
    }

    @Test
    void releaseForADeletedProductIsANoOp() {
        UUID id = createProduct(4, 100);
        productService.delete(id);

        inTransaction(() -> {
            stockService.release(id, 3);
            return null;
        });

        assertThat(productRepository.findById(id)).isEmpty();
    }

    @Test
    void callingWithoutATransactionIsRejected() {
        UUID id = createProduct(4, 100);

        assertThrows(IllegalTransactionStateException.class, () -> stockService.reserve(id, 1));

        assertThat(stockOf(id)).isEqualTo(4);
    }

    @Test
    void cachedProductShowsTheNewStockAfterCommit() {
        UUID id = createProduct(5, 100);
        productService.get(id);

        inTransaction(() -> stockService.reserve(id, 2));
        int afterReserve = productService.get(id).stock();
        inTransaction(() -> {
            stockService.release(id, 1);
            return null;
        });
        int afterRelease = productService.get(id).stock();

        assertThat(afterReserve).isEqualTo(3);
        assertThat(afterRelease).isEqualTo(4);
    }

    private <T> T inTransaction(Supplier<T> action) {
        return transactionTemplate.execute(status -> action.get());
    }

    private UUID createProduct(int stock, long priceCents) {
        return productService
                .create(new ProductRequest("Stock Item", "home", priceCents, stock, 4.0))
                .id();
    }

    private int stockOf(UUID id) {
        return productRepository.findById(id).orElseThrow().getStock();
    }
}
