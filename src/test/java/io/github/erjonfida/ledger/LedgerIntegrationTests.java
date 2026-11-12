package io.github.erjonfida.ledger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class LedgerIntegrationTests {

	@Autowired
	MockMvc mvc;

	@Autowired
	LedgerService ledger;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void depositTransferAndHistoryOverHttp() throws Exception {
		String alice = open("Alice", "EUR");
		String bob = open("Bob", "EUR");

		mvc.perform(deposit(alice, 10_000)).andExpect(status().isCreated());
		mvc.perform(transfer(alice, bob, 2_500))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.amount").value(2_500));

		mvc.perform(get("/api/accounts/{id}", alice)).andExpect(jsonPath("$.balance").value(7_500));
		mvc.perform(get("/api/accounts/{id}", bob)).andExpect(jsonPath("$.balance").value(2_500));
		mvc.perform(get("/api/accounts/{id}/entries", alice))
			.andExpect(jsonPath("$[0].amount").value(-2_500))
			.andExpect(jsonPath("$[1].amount").value(10_000));
		mvc.perform(get("/api/accounts/{id}/entries?size=1", alice)).andExpect(jsonPath("$.length()").value(1));
	}

	@Test
	void aRetryWithTheSameKeyReturnsTheFirstResponse() throws Exception {
		String alice = open("Alice", "EUR");
		String bob = open("Bob", "EUR");
		mvc.perform(deposit(alice, 1_000)).andExpect(status().isCreated());
		String key = UUID.randomUUID().toString();

		String first = mvc.perform(transfer(alice, bob, 300, key))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		String retry = mvc.perform(transfer(alice, bob, 300, key))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();

		assertThat(retry).isEqualTo(first);
		mvc.perform(get("/api/accounts/{id}", alice)).andExpect(jsonPath("$.balance").value(700));
		mvc.perform(transfer(alice, bob, 400, key))
			.andExpect(status().is(422))
			.andExpect(jsonPath("$.detail").value(containsString("different request")));
	}

	@Test
	void concurrentRetriesMoveTheMoneyOnce() throws Exception {
		UUID alice = ledger.open("Alice", "EUR").getId();
		UUID bob = ledger.open("Bob", "EUR").getId();
		ledger.deposit(alice, 1_000, UUID.randomUUID().toString());
		String key = UUID.randomUUID().toString();
		Callable<UUID> retry = () -> ledger.transfer(alice, bob, 300, key).getId();

		Set<UUID> transferIds = new HashSet<>();
		try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
			for (Future<UUID> result : pool.invokeAll(Collections.nCopies(20, retry))) {
				transferIds.add(result.get());
			}
		}

		assertThat(transferIds).hasSize(1);
		assertThat(ledger.account(alice).getBalance()).isEqualTo(700);
		assertThat(ledger.account(bob).getBalance()).isEqualTo(300);
	}

	@Test
	void aFailedTransferCanBeRetriedWithTheSameKey() throws Exception {
		String alice = open("Alice", "EUR");
		String bob = open("Bob", "EUR");
		String key = UUID.randomUUID().toString();

		mvc.perform(transfer(alice, bob, 500, key)).andExpect(status().isConflict());
		mvc.perform(deposit(alice, 500)).andExpect(status().isCreated());
		mvc.perform(transfer(alice, bob, 500, key)).andExpect(status().isCreated());

		mvc.perform(get("/api/accounts/{id}", bob)).andExpect(jsonPath("$.balance").value(500));
	}

	@Test
	void errorsComeBackAsProblemDetails() throws Exception {
		String alice = open("Alice", "EUR");
		String bob = open("Bob", "EUR");
		String carol = open("Carol", "USD");
		mvc.perform(deposit(alice, 100)).andExpect(status().isCreated());

		mvc.perform(transfer(alice, bob, 101))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value(containsString("does not have 101")));
		mvc.perform(transfer(alice, UUID.randomUUID().toString(), 1)).andExpect(status().isNotFound());
		mvc.perform(transfer(alice, carol, 1)).andExpect(status().isBadRequest());
		mvc.perform(transfer(alice, alice, 1)).andExpect(status().isBadRequest());
		mvc.perform(transfer(alice, bob, 0)).andExpect(status().isBadRequest());
		mvc.perform(transfer(alice, bob, 1, null)).andExpect(status().isBadRequest());
		mvc.perform(transfer(alice, bob, 1, " ")).andExpect(status().isBadRequest());
		mvc.perform(openRequest("Dave", "XXX")).andExpect(status().isBadRequest());
		mvc.perform(get("/api/accounts/{id}/entries?size=101", alice)).andExpect(status().isBadRequest());

		mvc.perform(get("/api/accounts/{id}", alice)).andExpect(jsonPath("$.balance").value(100));
	}

	@Test
	void randomTransfersKeepTheBooksBalanced() {
		Random random = new Random(42);
		List<UUID> accounts = new ArrayList<>();
		for (int i = 0; i < 20; i++) {
			UUID id = ledger.open("Account " + i, "EUR").getId();
			ledger.deposit(id, 100_000, UUID.randomUUID().toString());
			accounts.add(id);
		}

		int rejected = 0;
		for (int i = 0; i < 10_000; i++) {
			UUID from = accounts.get(random.nextInt(accounts.size()));
			UUID to = accounts.get(random.nextInt(accounts.size()));
			if (from.equals(to)) {
				continue;
			}
			try {
				ledger.transfer(from, to, 1 + random.nextInt(50_000), UUID.randomUUID().toString());
			}
			catch (InsufficientFundsException expected) {
				rejected++;
			}
		}

		assertThat(rejected).isPositive();
		assertThat(count("SELECT coalesce(sum(amount), 0) FROM entry")).isZero();
		assertThat(count("""
				SELECT count(*) FROM (
					SELECT transfer_id FROM entry GROUP BY transfer_id HAVING count(*) <> 2 OR sum(amount) <> 0
				) unbalanced""")).isZero();
		assertThat(count("""
				SELECT count(*) FROM account a
				WHERE a.balance <> (SELECT coalesce(sum(e.amount), 0) FROM entry e WHERE e.account_id = a.id)
				""")).isZero();
	}

	@Test
	void apiDocsAreServed() throws Exception {
		mvc.perform(get("/v3/api-docs")).andExpect(jsonPath("$.paths['/api/transfers']").exists());
	}

	private long count(String sql) {
		return jdbc.queryForObject(sql, Long.class);
	}

	private String open(String name, String currency) throws Exception {
		String created = mvc.perform(openRequest(name, currency))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		return JsonPath.read(created, "$.id");
	}

	private static MockHttpServletRequestBuilder openRequest(String name, String currency) {
		return post("/api/accounts").contentType(APPLICATION_JSON)
			.content("{\"name\": \"%s\", \"currency\": \"%s\"}".formatted(name, currency));
	}

	private static MockHttpServletRequestBuilder deposit(String account, long amount) {
		return post("/api/accounts/{id}/deposits", account).contentType(APPLICATION_JSON)
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.content("{\"amount\": %d}".formatted(amount));
	}

	private static MockHttpServletRequestBuilder transfer(String from, String to, long amount) {
		return transfer(from, to, amount, UUID.randomUUID().toString());
	}

	private static MockHttpServletRequestBuilder transfer(String from, String to, long amount, String key) {
		MockHttpServletRequestBuilder request = post("/api/transfers").contentType(APPLICATION_JSON)
			.content("{\"fromAccountId\": \"%s\", \"toAccountId\": \"%s\", \"amount\": %d}".formatted(from, to, amount));
		return key == null ? request : request.header("Idempotency-Key", key);
	}

}
