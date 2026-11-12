package io.github.erjonfida.ledger;

import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Account {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	private String name;

	private String currency;

	private boolean cash;

	private long balance;

	protected Account() {
	}

	Account(String name, String currency) {
		this.name = name;
		this.currency = currency;
	}

	public UUID getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getCurrency() {
		return currency;
	}

	public long getBalance() {
		return balance;
	}

}
