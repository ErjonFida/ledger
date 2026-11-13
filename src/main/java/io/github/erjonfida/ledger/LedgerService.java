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
	public Account open(UUID client, String name, String currency) {
		cashAccount(currency);
		return accounts.save(new Account(client, name, currency));
	}

	@Transactional(readOnly = true)
	public Account account(UUID client, UUID id) {
		return accounts.findById(id)
			.filter(account -> account.belongsTo(client))
			.orElseThrow(() -> new NoSuchElementException("Unknown account " + id));
	}

	@Transactional(readOnly = true)
	public List<Entry> entries(UUID client, UUID accountId, int page, int size) {
		account(client, accountId);
		return entries.findByAccountIdOrderByIdDesc(accountId, PageRequest.of(page, size));
	}

	@Transactional
	public Transfer deposit(UUID client, UUID accountId, long amount, String idempotencyKey) {
		Account cash = cashAccount(account(client, accountId).getCurrency());
		return move(cash.getId(), accountId, amount, idempotencyKey);
	}

	@Transactional
	public Transfer transfer(UUID client, UUID from, UUID to, long amount, String idempotencyKey) {
		if (from.equals(to)) {
			throw new IllegalArgumentException("Cannot transfer to the same account");
		}
		if (!account(client, from).getCurrency().equals(account(client, to).getCurrency())) {
			throw new IllegalArgumentException("The accounts are in different currencies");
		}
		return move(from, to, amount, idempotencyKey);
	}

	private Transfer move(UUID from, UUID to, long amount, String idempotencyKey) {
		Transfer transfer = new Transfer(from, to, amount, idempotencyKey);
		if (transfers.insertIfKeyIsNew(transfer) == 0) {
			return replay(transfer);
		}
		// Lower id first, so two opposite transfers lock the accounts in the same order and cannot deadlock
		if (from.compareTo(to) < 0) {
			debit(from, amount);
			accounts.credit(to, amount);
		}
		else {
			accounts.credit(to, amount);
			debit(from, amount);
		}
		entries.save(new Entry(transfer.getId(), from, -amount));
		entries.save(new Entry(transfer.getId(), to, amount));
		return transfer;
	}

	private void debit(UUID account, long amount) {
		if (accounts.debit(account, amount) == 0) {
			throw new InsufficientFundsException(account, amount);
		}
	}

	private Transfer replay(Transfer request) {
		Transfer first = transfers.findByIdempotencyKey(request.getIdempotencyKey()).orElseThrow();
		if (!first.isSameRequestAs(request)) {
			throw new IdempotencyKeyReusedException(request.getIdempotencyKey());
		}
		return first;
	}

	private Account cashAccount(String currency) {
		return accounts.findByCurrencyAndCashTrue(currency)
			.orElseThrow(() -> new IllegalArgumentException("Unsupported currency " + currency));
	}

}
