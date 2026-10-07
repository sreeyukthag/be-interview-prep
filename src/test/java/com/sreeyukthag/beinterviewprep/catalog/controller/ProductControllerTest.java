package com.sreeyukthag.beinterviewprep.catalog.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sreeyukthag.beinterviewprep.catalog.dto.request.ProductFilter;
import com.sreeyukthag.beinterviewprep.catalog.dto.request.ProductRequest;
import com.sreeyukthag.beinterviewprep.catalog.dto.response.ProductResponse;
import com.sreeyukthag.beinterviewprep.catalog.service.ProductService;
import com.sreeyukthag.beinterviewprep.common.exception.ResourceNotFoundException;
import com.sreeyukthag.beinterviewprep.common.web.PageResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProductController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProductControllerTest {

    private static final UUID ID = UUID.fromString("8aa10fd1-20a5-5dea-88e0-ffd88a8d048b");
    private static final String VALID_BODY = """
            {"name":"Desk Lamp","category":"home","priceCents":4999,"stock":7,"rating":4.5}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    @Test
    void listPassesEveryFilterAndPagingParameterInOneRequest() throws Exception {
        PageResponse<ProductResponse> page = new PageResponse<>(List.of(sampleResponse()), 2, 5, 11, 3, false, true);
        when(productService.search(any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/products")
                        .param("category", "home")
                        .param("minPrice", "1000")
                        .param("maxPrice", "5000")
                        .param("inStock", "true")
                        .param("q", "lamp")
                        .param("page", "2")
                        .param("size", "5")
                        .param("sort", "priceCents,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].name").value("Desk Lamp"))
                .andExpect(jsonPath("$.data.totalElements").value(11))
                .andExpect(jsonPath("$.data.totalPages").value(3));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(productService).search(eq(new ProductFilter("home", 1000L, 5000L, true, "lamp")), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(5);
        assertThat(pageable.getValue().getSort())
                .isEqualTo(Sort.by(Sort.Order.desc("priceCents"), Sort.Order.asc("id")));
    }

    @Test
    void listDefaultsToNewestFirstWithNoFilters() throws Exception {
        when(productService.search(any(), any())).thenReturn(emptyPage());

        mockMvc.perform(get("/api/v1/products")).andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(productService).search(eq(ProductFilter.none()), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
        assertThat(pageable.getValue().getSort())
                .isEqualTo(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id")));
    }

    @Test
    void pageSizeAboveOneHundredIsCappedAtOneHundred() throws Exception {
        when(productService.search(any(), any())).thenReturn(emptyPage());

        mockMvc.perform(get("/api/v1/products").param("size", "500")).andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(productService).search(any(), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    void unknownSortFieldReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/products").param("sort", "secret,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_SORT"));

        verifyNoInteractions(productService);
    }

    @Test
    void invertedPriceRangeReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/products").param("minPrice", "5000").param("maxPrice", "1000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("priceRangeValid"));

        verifyNoInteractions(productService);
    }

    @Test
    void nonNumericPriceReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/products").param("minPrice", "cheap"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("minPrice"));
    }

    @Test
    void unknownProductReturns404() throws Exception {
        when(productService.get(ID)).thenThrow(new ResourceNotFoundException("Product", ID));

        mockMvc.perform(get("/api/v1/products/{id}", ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
    }

    @Test
    void createReturns201WithLocation() throws Exception {
        when(productService.create(any())).thenReturn(sampleResponse());

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/products/" + ID))
                .andExpect(jsonPath("$.data.id").value(ID.toString()));
    }

    @Test
    void createWithInvalidFieldsReturns400PerField() throws Exception {
        String body = """
                {"name":"","category":"home","priceCents":-1,"stock":7,"rating":6}
                """;

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(3));

        verifyNoInteractions(productService);
    }

    @Test
    void updateReturnsTheUpdatedProduct() throws Exception {
        when(productService.update(eq(ID), any(ProductRequest.class))).thenReturn(sampleResponse());

        mockMvc.perform(put("/api/v1/products/{id}", ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.priceCents").value(4999));
    }

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/api/v1/products/{id}", ID)).andExpect(status().isNoContent());

        verify(productService).delete(ID);
    }

    private static ProductResponse sampleResponse() {
        Instant now = Instant.parse("2026-01-01T09:00:00Z");
        return new ProductResponse(ID, "Desk Lamp", "home", 4999, 7, 4.5, now, now);
    }

    private static PageResponse<ProductResponse> emptyPage() {
        return new PageResponse<>(List.of(), 0, 20, 0, 0, true, true);
    }
}
