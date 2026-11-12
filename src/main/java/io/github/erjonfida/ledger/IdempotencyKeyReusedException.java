package io.github.erjonfida.ledger;

public class IdempotencyKeyReusedException extends RuntimeException {

	IdempotencyKeyReusedException(String key) {
		super("Idempotency-Key " + key + " was already used for a different request");
	}

}
