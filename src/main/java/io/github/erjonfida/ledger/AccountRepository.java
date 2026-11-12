package io.github.erjonfida.ledger;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface AccountRepository extends JpaRepository<Account, UUID> {

	Optional<Account> findByCurrencyAndCashTrue(String currency);

	// Returns 1 if debited, 0 if the balance does not cover the amount
	@Modifying
	@Query(value = "UPDATE account SET balance = balance - :amount WHERE id = :id AND (cash OR balance >= :amount)",
			nativeQuery = true)
	int debit(UUID id, long amount);

	@Modifying
	@Query(value = "UPDATE account SET balance = balance + :amount WHERE id = :id", nativeQuery = true)
	void credit(UUID id, long amount);

}
