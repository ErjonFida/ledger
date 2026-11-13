package io.github.erjonfida.ledger;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface ClientRepository extends JpaRepository<Client, UUID> {

	Optional<Client> findByApiKeyHash(String apiKeyHash);

}
