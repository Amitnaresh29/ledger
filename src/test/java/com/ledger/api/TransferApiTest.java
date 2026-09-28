package com.ledger.api;

import com.ledger.account.Account;
import com.ledger.account.AccountRepository;
import com.ledger.transfer.TransferService;
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

// These four are STATIC imports - you call post(...) and status(), not
// MockMvcRequestBuilders.post(...). Getting these wrong is the usual first
// stumble with MockMvc, because the IDE offers several classes named similarly.
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests the HTTP layer: routing, JSON shape, status codes, exception mapping.
 *
 * @SpringBootTest loads the WHOLE application - controllers, service, repositories,
 * the exception handler, Flyway. @AutoConfigureMockMvc then gives us a MockMvc
 * bean.
 *
 * MockMvc drives the real Spring MVC machinery - request mapping, argument
 * binding, @Valid, Jackson serialisation, @RestControllerAdvice - WITHOUT opening
 * a socket or starting Tomcat. So it is far faster than a real HTTP call while
 * still exercising everything a real request would touch.
 *
 * Note this test is NOT transactional (unlike @DataJpaTest), so rows persist
 * between test methods. That is why every test creates its own fresh accounts
 * with random UUIDs instead of sharing fixtures.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class TransferApiTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired MockMvc mockMvc;
    @Autowired AccountRepository accounts;
    @Autowired TransferService transferService;

    private Account account(String type) {
        return accounts.save(new Account(UUID.randomUUID(), type, UUID.randomUUID(), "INR"));
    }

    /** Money must come from somewhere: an EQUITY account may go negative, an ASSET one may not. */
    private void fund(Account target, long amountMinor) {
        transferService.transfer("fund-" + UUID.randomUUID(), account("EQUITY").getId(),
                target.getId(), amountMinor, "funding");
    }

    @Test
    void post_transfers_returns_201_with_a_transaction_id() throws Exception {
        Account from = account("ASSET");
        Account to = account("ASSET");
        fund(from, 100_000L);

        // A TEXT BLOCK (Java 15+): a multi-line string literal, so the JSON stays
        // readable instead of becoming "{\"fromAccountId\":\"" + ... + "\"}".
        // .formatted(...) substitutes the %s placeholders - same as String.format.
        String body = """
                {
                  "fromAccountId": "%s",
                  "toAccountId": "%s",
                  "amountMinor": 50000,
                  "description": "A pays B Rs 500"
                }
                """.formatted(from.getId(), to.getId());

        mockMvc.perform(post("/transfers")
                        .header("Idempotency-Key", "api-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)   // without this, 415 Unsupported Media Type
                        .content(body))
                // Each andExpect is an assertion. The first failure reports the
                // whole request AND response, which makes debugging quick.
                .andExpect(status().isCreated())                   // 201
                // jsonPath walks the response JSON. "$" is the root, so
                // "$.transactionId" is the top-level field of that name.
                .andExpect(jsonPath("$.transactionId").isNotEmpty())
                .andExpect(jsonPath("$.createdAt").isNotEmpty());  // proves the @Generated read-back
    }
}
