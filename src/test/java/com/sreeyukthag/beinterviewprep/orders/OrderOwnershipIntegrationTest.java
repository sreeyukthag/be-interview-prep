package com.sreeyukthag.beinterviewprep.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.sreeyukthag.beinterviewprep.auth.AuthTestClient;
import com.sreeyukthag.beinterviewprep.catalog.dto.request.ProductRequest;
import com.sreeyukthag.beinterviewprep.catalog.service.ProductService;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
class OrderOwnershipIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProductService productService;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private AuthTestClient client;

    @BeforeEach
    void setUp() {
        client = new AuthTestClient(mockMvc, objectMapper);
    }

    static Stream<MockHttpServletRequestBuilder> orderEndpoints() {
        UUID orderId = UUID.randomUUID();
        return Stream.of(
                post("/api/v1/orders")
                        .header("Idempotency-Key", "no-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productId\":\"%s\",\"quantity\":1}]}".formatted(UUID.randomUUID())),
                get("/api/v1/orders/{id}", orderId),
                post("/api/v1/orders/{id}/cancel", orderId));
    }

    @ParameterizedTest
    @MethodSource("orderEndpoints")
    void orderEndpointsWithoutATokenReturn401(MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    void placedOrderBelongsToTheTokenSubjectEvenIfTheBodyNamesSomeoneElse() throws Exception {
        UUID productId = createProduct(5);
        String token = client.registerAndLogin(AuthTestClient.uniqueEmail());
        UUID tokenSubject = UUID.fromString(jwtDecoder.decode(token).getSubject());
        String body = """
                {"customerId":"%s","items":[{"productId":"%s","quantity":1}]}
                """.formatted(UUID.randomUUID(), productId);

        String response = mockMvc.perform(post("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID orderId = UUID.fromString(JsonPath.read(response, "$.data.id"));
        assertThat(ownerOf(orderId)).isEqualTo(tokenSubject);
    }

    private UUID createProduct(int stock) {
        return productService
                .create(new ProductRequest("Owned Item", "home", 1_000L, stock, 4.0))
                .id();
    }

    private UUID ownerOf(UUID orderId) {
        return jdbcTemplate.queryForObject("SELECT customer_id FROM orders WHERE id = ?", UUID.class, orderId);
    }
}
