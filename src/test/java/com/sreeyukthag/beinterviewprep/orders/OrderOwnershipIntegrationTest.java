package com.sreeyukthag.beinterviewprep.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.sreeyukthag.beinterviewprep.auth.AuthTestClient;
import com.sreeyukthag.beinterviewprep.auth.TestTokens;
import com.sreeyukthag.beinterviewprep.catalog.dto.request.ProductRequest;
import com.sreeyukthag.beinterviewprep.catalog.service.ProductService;
import java.time.Duration;
import java.time.Instant;
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
import org.springframework.security.oauth2.jwt.JwtEncoder;
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
    private JwtEncoder jwtEncoder;

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
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
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

    @Test
    void aCustomerCannotSeeOrCancelAnotherCustomersOrder() throws Exception {
        UUID productId = createProduct(5);
        String alice = client.registerAndLogin(AuthTestClient.uniqueEmail());
        String bob = client.registerAndLogin(AuthTestClient.uniqueEmail());
        UUID bobsOrder = placeAs(bob, productId, 2);

        mockMvc.perform(get("/api/v1/orders/{id}", bobsOrder).header(HttpHeaders.AUTHORIZATION, bearer(alice)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
        mockMvc.perform(post("/api/v1/orders/{id}/cancel", bobsOrder).header(HttpHeaders.AUTHORIZATION, bearer(alice)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));

        assertThat(stockOf(productId)).isEqualTo(3);
        mockMvc.perform(get("/api/v1/orders/{id}", bobsOrder).header(HttpHeaders.AUTHORIZATION, bearer(bob)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PLACED"));
    }

    @Test
    void anAdminCanReadAndCancelAnotherCustomersOrder() throws Exception {
        UUID productId = createProduct(5);
        String bob = client.registerAndLogin(AuthTestClient.uniqueEmail());
        UUID bobsOrder = placeAs(bob, productId, 2);
        Instant now = Instant.now();
        String admin = TestTokens.signed(jwtEncoder, "ADMIN", now, now.plus(Duration.ofMinutes(15)));

        mockMvc.perform(get("/api/v1/orders/{id}", bobsOrder).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(bobsOrder.toString()));
        mockMvc.perform(post("/api/v1/orders/{id}/cancel", bobsOrder).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        assertThat(stockOf(productId)).isEqualTo(5);
    }

    private UUID placeAs(String token, UUID productId, int quantity) throws Exception {
        String response = mockMvc.perform(post("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productId\":\"%s\",\"quantity\":%d}]}".formatted(productId, quantity)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return UUID.fromString(JsonPath.read(response, "$.data.id"));
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private int stockOf(UUID productId) {
        return jdbcTemplate.queryForObject("SELECT stock FROM products WHERE id = ?", Integer.class, productId);
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
