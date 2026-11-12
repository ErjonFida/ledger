package io.github.erjonfida.ledger;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import org.hibernate.annotations.Immutable;

@Entity
@Immutable
public class Transfer {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	private UUID fromAccountId;

	private UUID toAccountId;

	private long amount;

	private Instant createdAt;

	protected Transfer() {
	}

	Transfer(UUID fromAccountId, UUID toAccountId, long amount) {
		this.fromAccountId = fromAccountId;
		this.toAccountId = toAccountId;
		this.amount = amount;
		this.createdAt = Instant.now();
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

}
