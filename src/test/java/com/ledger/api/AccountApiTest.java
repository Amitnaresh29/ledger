package com.ledger.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AccountApiTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired MockMvc mockMvc;

    private String body(String accountType, String currency) {
        return """
                {"ownerId": "%s", "accountType": "%s", "currency": "%s"}
                """.formatted(UUID.randomUUID(), accountType, currency);
    }

    @Test
    void post_accounts_creates_an_account_and_returns_201() throws Exception {
        mockMvc.perform(post("/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("ASSET", "INR")))
                .andExpect(status().isCreated())
                // The server generated an id the client never supplied...
                .andExpect(jsonPath("$.id").isNotEmpty())
                // ...and createdAt came back populated, which only works because
                // the controller returns what save() gave it.
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.accountType").value("ASSET"))
                .andExpect(jsonPath("$.currency").value("INR"));
    }

    @Test
    void post_accounts_rejects_an_unknown_account_type() throws Exception {
        // "WALLET" is not one of the five. The DB CHECK would also catch this,
        // but @Pattern catches it first and names the field.
        mockMvc.perform(post("/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("WALLET", "INR")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_failed"))
                .andExpect(jsonPath("$.fields.accountType").exists());
    }

    @Test
    void post_accounts_rejects_a_badly_formed_currency() throws Exception {
        mockMvc.perform(post("/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("ASSET", "inr")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.currency").exists());
    }

    @Test
    void post_accounts_rejects_a_missing_owner() throws Exception {
        mockMvc.perform(post("/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accountType": "ASSET", "currency": "INR"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.ownerId").exists());
    }
}
