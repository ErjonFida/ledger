package io.github.erjonfida.ledger;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import org.hibernate.annotations.Immutable;

@Entity
@Immutable
public class Transfer {

	@Id
	private UUID id;

	private UUID fromAccountId;

	private UUID toAccountId;

	private long amount;

	private Instant createdAt;

	private String idempotencyKey;

	protected Transfer() {
	}

	Transfer(UUID fromAccountId, UUID toAccountId, long amount, String idempotencyKey) {
		this.id = UUID.randomUUID();
		this.fromAccountId = fromAccountId;
		this.toAccountId = toAccountId;
		this.amount = amount;
		this.createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
		this.idempotencyKey = idempotencyKey;
	}

	boolean isSameRequestAs(Transfer other) {
		return fromAccountId.equals(other.fromAccountId) && toAccountId.equals(other.toAccountId)
				&& amount == other.amount;
	}

	public UUID getId() {
		return id;
	}

	public UUID getFromAccountId() {
		return fromAccountId;
	}

	public UUID getToAccountId() {
		return toAccountId;
	}

	public long getAmount() {
		return amount;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public String getIdempotencyKey() {
		return idempotencyKey;
	}

}
