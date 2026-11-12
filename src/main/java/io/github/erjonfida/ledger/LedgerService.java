package io.github.erjonfida.ledger;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerService {

	private final AccountRepository accounts;

	private final TransferRepository transfers;

	private final EntryRepository entries;

	LedgerService(AccountRepository accounts, TransferRepository transfers, EntryRepository entries) {
		this.accounts = accounts;
		this.transfers = transfers;
		this.entries = entries;
	}

	@Transactional
	public Account open(String name, String currency) {
		cashAccount(currency);
		return accounts.save(new Account(name, currency));
	}

	@Transactional(readOnly = true)
	public Account account(UUID id) {
		return accounts.findById(id).orElseThrow(() -> new NoSuchElementException("Unknown account " + id));
	}

	@Transactional(readOnly = true)
	public List<Entry> entries(UUID accountId, int page, int size) {
		account(accountId);
		return entries.findByAccountIdOrderByIdDesc(accountId, PageRequest.of(page, size));
	}

	@Transactional
	public Transfer deposit(UUID accountId, long amount) {
		Account cash = cashAccount(account(accountId).getCurrency());
		return transfer(cash.getId(), accountId, amount);
	}

	@Transactional
	public Transfer transfer(UUID from, UUID to, long amount) {
		if (from.equals(to)) {
			throw new IllegalArgumentException("Cannot transfer to the same account");
		}
		if (!account(from).getCurrency().equals(account(to).getCurrency())) {
			throw new IllegalArgumentException("The accounts are in different currencies");
		}
		// ponytail: rows are updated sender first, so two opposite transfers can deadlock; update in id order (roadmap 2)
		if (accounts.debit(from, amount) == 0) {
			throw new InsufficientFundsException(from, amount);
		}
		accounts.credit(to, amount);
		Transfer transfer = transfers.save(new Transfer(from, to, amount));
		entries.save(new Entry(transfer.getId(), from, -amount));
		entries.save(new Entry(transfer.getId(), to, amount));
		return transfer;
	}

	private Account cashAccount(String currency) {
		return accounts.findByCurrencyAndCashTrue(currency)
			.orElseThrow(() -> new IllegalArgumentException("Unsupported currency " + currency));
	}

}
