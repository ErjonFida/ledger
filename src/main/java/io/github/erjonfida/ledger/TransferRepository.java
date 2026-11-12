package io.github.erjonfida.ledger;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface TransferRepository extends JpaRepository<Transfer, UUID> {

}
