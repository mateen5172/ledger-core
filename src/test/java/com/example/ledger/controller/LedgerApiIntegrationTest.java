package com.example.ledger.controller;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** End-to-end HTTP tests: controller → service → Hibernate → H2. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LedgerApiIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void depositTransferReplayAndStatementFlow() throws Exception {
        String alice = createAccount("Alice", "USD");
        String bob = createAccount("Bob", "USD");

        mvc.perform(post("/api/accounts/{id}/deposits", alice)
                        .header("Idempotency-Key", key()).contentType(APPLICATION_JSON)
                        .content("{\"amount\":10000,\"description\":\"top-up\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("DEPOSIT"))
                .andExpect(jsonPath("$.entries.length()").value(2));

        String transferKey = key();
        String transferBody = """
                {"fromAccountId":"%s","toAccountId":"%s","amount":2500,"description":"rent"}
                """.formatted(alice, bob);

        MvcResult first = mvc.perform(post("/api/transfers")
                        .header("Idempotency-Key", transferKey).contentType(APPLICATION_JSON).content(transferBody))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andReturn();
        String transactionId = JsonPath.read(first.getResponse().getContentAsString(), "$.id");

        // Retrying with the same key returns the original transaction and does not move money again
        mvc.perform(post("/api/transfers")
                        .header("Idempotency-Key", transferKey).contentType(APPLICATION_JSON).content(transferBody))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(jsonPath("$.id").value(transactionId));

        mvc.perform(get("/api/accounts/{id}", alice))
                .andExpect(jsonPath("$.balance").value(7500));
        mvc.perform(get("/api/accounts/{id}", bob))
                .andExpect(jsonPath("$.balance").value(2500));

        mvc.perform(get("/api/accounts/{id}/entries", alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].balanceAfter").value(7500));

        mvc.perform(get("/api/ledger/integrity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consistent").value(true));
    }

    @Test
    void reversalRestoresBalancesAndCanBeRepeatedSafely() throws Exception {
        String alice = createAccount("Alice", "USD");
        String bob = createAccount("Bob", "USD");
        deposit(alice, 5000);

        MvcResult transfer = mvc.perform(post("/api/transfers")
                        .header("Idempotency-Key", key()).contentType(APPLICATION_JSON)
                        .content("{\"fromAccountId\":\"%s\",\"toAccountId\":\"%s\",\"amount\":3000}"
                                .formatted(alice, bob)))
                .andExpect(status().isCreated())
                .andReturn();
        String transactionId = JsonPath.read(transfer.getResponse().getContentAsString(), "$.id");

        mvc.perform(post("/api/transactions/{id}/reverse", transactionId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("REVERSAL"))
                .andExpect(jsonPath("$.reversalOf").value(transactionId));
        mvc.perform(post("/api/transactions/{id}/reverse", transactionId))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replayed", "true"));

        mvc.perform(get("/api/accounts/{id}", alice)).andExpect(jsonPath("$.balance").value(5000));
        mvc.perform(get("/api/accounts/{id}", bob)).andExpect(jsonPath("$.balance").value(0));
    }

    @Test
    void insufficientFundsReturns422ProblemDetail() throws Exception {
        String alice = createAccount("Alice", "USD");
        String bob = createAccount("Bob", "USD");
        deposit(alice, 100);

        mvc.perform(post("/api/transfers")
                        .header("Idempotency-Key", key()).contentType(APPLICATION_JSON)
                        .content("{\"fromAccountId\":\"%s\",\"toAccountId\":\"%s\",\"amount\":101}"
                                .formatted(alice, bob)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Insufficient funds"));
    }

    @Test
    void reusingKeyWithDifferentAmountReturns409() throws Exception {
        String alice = createAccount("Alice", "USD");
        String bob = createAccount("Bob", "USD");
        deposit(alice, 10_000);
        String key = key();

        mvc.perform(post("/api/transfers").header("Idempotency-Key", key).contentType(APPLICATION_JSON)
                        .content("{\"fromAccountId\":\"%s\",\"toAccountId\":\"%s\",\"amount\":100}".formatted(alice, bob)))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/transfers").header("Idempotency-Key", key).contentType(APPLICATION_JSON)
                        .content("{\"fromAccountId\":\"%s\",\"toAccountId\":\"%s\",\"amount\":200}".formatted(alice, bob)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Idempotency key reuse"));
    }

    @Test
    void missingIdempotencyKeyIsRejected() throws Exception {
        String alice = createAccount("Alice", "USD");

        mvc.perform(post("/api/accounts/{id}/deposits", alice).contentType(APPLICATION_JSON)
                        .content("{\"amount\":100}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidRequestsReturnValidationProblems() throws Exception {
        mvc.perform(post("/api/accounts").contentType(APPLICATION_JSON)
                        .content("{\"ownerName\":\"\",\"currency\":\"usd\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.ownerName").exists())
                .andExpect(jsonPath("$.errors.currency").exists());

        mvc.perform(post("/api/accounts").contentType(APPLICATION_JSON)
                        .content("{\"ownerName\":\"Zed\",\"currency\":\"XYZ\"}"))
                .andExpect(status().isBadRequest());

        String alice = createAccount("Alice", "USD");
        mvc.perform(post("/api/accounts/{id}/deposits", alice)
                        .header("Idempotency-Key", key()).contentType(APPLICATION_JSON)
                        .content("{\"amount\":-5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.amount").exists());
    }

    @Test
    void unknownAccountReturns404() throws Exception {
        mvc.perform(get("/api/accounts/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void transferToSameAccountReturns422() throws Exception {
        String alice = createAccount("Alice", "USD");
        deposit(alice, 1000);

        mvc.perform(post("/api/transfers").header("Idempotency-Key", key()).contentType(APPLICATION_JSON)
                        .content("{\"fromAccountId\":\"%s\",\"toAccountId\":\"%s\",\"amount\":10}".formatted(alice, alice)))
                .andExpect(status().isUnprocessableEntity());
    }

    // ----- helpers -----

    private String createAccount(String owner, String currency) throws Exception {
        MvcResult result = mvc.perform(post("/api/accounts").contentType(APPLICATION_JSON)
                        .content("{\"ownerName\":\"%s\",\"currency\":\"%s\"}".formatted(owner, currency)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private void deposit(String accountId, long amount) throws Exception {
        mvc.perform(post("/api/accounts/{id}/deposits", accountId)
                        .header("Idempotency-Key", key()).contentType(APPLICATION_JSON)
                        .content("{\"amount\":%d}".formatted(amount)))
                .andExpect(status().isCreated());
    }

    private String key() {
        return UUID.randomUUID().toString();
    }
}
