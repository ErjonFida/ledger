package io.github.erjonfida.ledger;

import java.util.UUID;

public class InsufficientFundsException extends RuntimeException {

	InsufficientFundsException(UUID accountId, long amount) {
		super("Account " + accountId + " does not have " + amount + " to send");
	}

}
