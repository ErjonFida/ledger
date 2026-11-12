package io.github.erjonfida.ledger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.ToDoubleFunction;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

// Not part of ./mvnw verify (Surefire only runs *Test and *Tests classes). Run it with:
//   ./mvnw test -Dtest=TransferBenchmark
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TransferBenchmark {

	static final int THREADS = 200;

	static final Duration ROUND = Duration.ofSeconds(10);

	static final int ROUNDS = 3;

	static final long START_BALANCE = 1_000_000;

	enum Outcome { DONE, DEADLOCK, TIME_UP }

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	TransactionTemplate tx;

	final AtomicInteger deadlocks = new AtomicInteger();

	volatile long deadline;

	Outcome senderFirst(UUID from, UUID to) {
		return attempt(() -> {
			debit(from);
			credit(to);
		});
	}

	Outcome senderFirstWithRetry(UUID from, UUID to) {
		Outcome outcome;
		do {
			outcome = senderFirst(from, to);
		}
		while (outcome == Outcome.DEADLOCK);
		return outcome;
	}

	Outcome lowerIdFirst(UUID from, UUID to) {
		return attempt(() -> {
			if (from.compareTo(to) < 0) {
				debit(from);
				credit(to);
			}
			else {
				credit(to);
				debit(from);
			}
		});
	}

	Outcome attempt(Runnable updates) {
		try {
			return tx.execute(status -> {
				if (timeUp()) {
					return Outcome.TIME_UP;
				}
				updates.run();
				return Outcome.DONE;
			});
		}
		catch (PessimisticLockingFailureException deadlock) {
			deadlocks.incrementAndGet();
			return Outcome.DEADLOCK;
		}
	}

	void debit(UUID account) {
		jdbc.update("UPDATE account SET balance = balance - 1 WHERE id = ? AND (cash OR balance >= 1)", account);
	}

	void credit(UUID account) {
		jdbc.update("UPDATE account SET balance = balance + 1 WHERE id = ?", account);
	}

	record Round(double throughput, double failedPercent, double deadlocksPer100, double p95Millis) {
	}

	@Test
	void opposingTransfers() throws Exception {
		Map<String, BiFunction<UUID, UUID, Outcome>> strategies = new LinkedHashMap<>();
		strategies.put("Sender first", this::senderFirst);
		strategies.put("Sender first, retry on deadlock", this::senderFirstWithRetry);
		strategies.put("Lower id first", this::lowerIdFirst);

		StringBuilder table = new StringBuilder("""
				| Strategy | Transfers/s | Failed | Deadlocks per 100 transfers | p95 latency (ms) |
				|---|--:|--:|--:|--:|
				""");
		for (var strategy : strategies.entrySet()) {
			round(strategy.getValue());
			List<Round> rounds = new ArrayList<>();
			for (int i = 0; i < ROUNDS; i++) {
				rounds.add(round(strategy.getValue()));
			}
			table.append(String.format(Locale.ROOT, "| %s | %.1f | %.0f%% | %.0f | %.0f |%n", strategy.getKey(),
					median(rounds, Round::throughput), median(rounds, Round::failedPercent),
					median(rounds, Round::deadlocksPer100), median(rounds, Round::p95Millis)));
			if (strategy.getKey().equals("Lower id first")) {
				assertThat(rounds).allSatisfy(r -> assertThat(r.deadlocksPer100()).isZero());
			}
		}

		System.out.println(table);
		Files.writeString(Path.of("target", "benchmark.md"), table);
	}

	Round round(BiFunction<UUID, UUID, Outcome> strategy) throws Exception {
		UUID a = openAccount();
		UUID b = openAccount();
		deadlocks.set(0);
		AtomicInteger failed = new AtomicInteger();
		Queue<Long> latencies = new ConcurrentLinkedQueue<>();
		CountDownLatch go = new CountDownLatch(1);
		List<Future<?>> threads = new ArrayList<>();

		long start;
		try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
			for (int i = 0; i < THREADS; i++) {
				UUID from = i % 2 == 0 ? a : b;
				UUID to = i % 2 == 0 ? b : a;
				threads.add(pool.submit(() -> {
					go.await();
					while (!timeUp()) {
						long t = System.nanoTime();
						switch (strategy.apply(from, to)) {
							case DONE -> latencies.add(System.nanoTime() - t);
							case DEADLOCK -> failed.incrementAndGet();
							case TIME_UP -> {
							}
						}
					}
					return null;
				}));
			}
			start = System.nanoTime();
			deadline = start + ROUND.toNanos();
			go.countDown();
		}
		double seconds = (System.nanoTime() - start) / 1e9;
		for (Future<?> thread : threads) {
			thread.get();
		}

		assertThat(balance(a) + balance(b)).isEqualTo(2 * START_BALANCE);
		long[] sorted = latencies.stream().mapToLong(Long::longValue).sorted().toArray();
		int done = sorted.length;
		return new Round(done / seconds, 100.0 * failed.get() / (done + failed.get()),
				100.0 * deadlocks.get() / done, sorted[done * 95 / 100] / 1e6);
	}

	boolean timeUp() {
		return System.nanoTime() - deadline > 0;
	}

	UUID openAccount() {
		UUID id = UUID.randomUUID();
		jdbc.update("INSERT INTO account (id, name, currency, balance) VALUES (?, 'Benchmark', 'EUR', ?)", id,
				START_BALANCE);
		return id;
	}

	long balance(UUID account) {
		return jdbc.queryForObject("SELECT balance FROM account WHERE id = ?", Long.class, account);
	}

	static double median(List<Round> rounds, ToDoubleFunction<Round> metric) {
		double[] values = rounds.stream().mapToDouble(metric).sorted().toArray();
		return values[values.length / 2];
	}

}
