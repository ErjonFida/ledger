package io.github.erjonfida.ledger;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface EntryRepository extends JpaRepository<Entry, Long> {

	List<Entry> findByAccountIdOrderByIdDesc(UUID accountId, Pageable page);

}
