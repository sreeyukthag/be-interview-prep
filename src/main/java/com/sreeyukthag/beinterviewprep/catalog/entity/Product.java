package com.sreeyukthag.beinterviewprep.catalog.entity;

import com.sreeyukthag.beinterviewprep.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "products")
public class Product extends BaseEntity {

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 50)
    private String category;

    @Column(name = "price_cents", nullable = false)
    private long priceCents;

    @Column(nullable = false)
    private int stock;

    @Column(nullable = false)
    private double rating;
}
