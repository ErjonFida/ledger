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
public class Entry {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private UUID transferId;

	private UUID accountId;

	private long amount;

	private Instant createdAt;

	protected Entry() {
	}

	Entry(UUID transferId, UUID accountId, long amount) {
		this.transferId = transferId;
		this.accountId = accountId;
		this.amount = amount;
		this.createdAt = Instant.now();
	}

	public UUID getTransferId() {
		return transferId;
	}

	public UUID getAccountId() {
		return accountId;
	}

	public long getAmount() {
		return amount;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
