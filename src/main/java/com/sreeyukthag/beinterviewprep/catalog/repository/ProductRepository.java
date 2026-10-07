package com.sreeyukthag.beinterviewprep.catalog.repository;

import com.sreeyukthag.beinterviewprep.catalog.entity.Product;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {

    /**
     * Check and decrement in one statement: the row lock it takes serialises concurrent orders for the same
     * product, and each waiter re-checks the condition against the committed stock, so stock never goes negative.
     */
    @Modifying
    @Query("UPDATE Product p SET p.stock = p.stock - :quantity WHERE p.id = :id AND p.stock >= :quantity")
    int decrementStock(@Param("id") UUID id, @Param("quantity") int quantity);

    @Modifying
    @Query("UPDATE Product p SET p.stock = p.stock + :quantity WHERE p.id = :id")
    int incrementStock(@Param("id") UUID id, @Param("quantity") int quantity);

    Optional<ProductStockSnapshot> findStockSnapshotById(UUID id);
}
