package io.github.erjonfida.ledger;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LedgerServiceTests {

	@Mock
	AccountRepository accounts;

	@Mock
	TransferRepository transfers;

	@Mock
	EntryRepository entries;

	@InjectMocks
	LedgerService ledger;

	final UUID alice = UUID.randomUUID();

	final UUID bob = UUID.randomUUID();

	@Test
	void aTransferWritesTwoEntriesThatSumToZero() {
		bothInEuros();
		when(transfers.insertIfKeyIsNew(any())).thenReturn(1);
		when(accounts.debit(alice, 500)).thenReturn(1);

		ledger.transfer(alice, bob, 500, "key-1");

		verify(accounts).credit(bob, 500);
		ArgumentCaptor<Entry> written = ArgumentCaptor.forClass(Entry.class);
		verify(entries, times(2)).save(written.capture());
		assertThat(written.getAllValues()).extracting(Entry::getAccountId).containsExactly(alice, bob);
		assertThat(written.getAllValues()).extracting(Entry::getAmount).containsExactly(-500L, 500L);
	}

	@Test
	void aTransferOverTheBalanceIsRejected() {
		bothInEuros();
		when(transfers.insertIfKeyIsNew(any())).thenReturn(1);
		when(accounts.debit(alice, 500)).thenReturn(0);

		assertThatThrownBy(() -> ledger.transfer(alice, bob, 500, "key-1"))
			.isInstanceOf(InsufficientFundsException.class);

		verifyNoInteractions(entries);
	}

	@Test
	void theAccountWithTheLowerIdIsUpdatedFirst() {
		UUID low = new UUID(0, 1);
		UUID high = new UUID(0, 2);
		when(accounts.findById(any())).thenReturn(Optional.of(new Account("Any", "EUR")));
		when(transfers.insertIfKeyIsNew(any())).thenReturn(1);
		when(accounts.debit(any(), anyLong())).thenReturn(1);

		ledger.transfer(low, high, 500, "key-1");
		ledger.transfer(high, low, 500, "key-2");

		InOrder order = inOrder(accounts);
		order.verify(accounts).debit(low, 500);
		order.verify(accounts).credit(high, 500);
		order.verify(accounts).credit(low, 500);
		order.verify(accounts).debit(high, 500);
	}

	@Test
	void aRepeatedKeyReturnsTheFirstTransferAndMovesNoMoney() {
		bothInEuros();
		Transfer first = new Transfer(alice, bob, 500, "key-1");
		when(transfers.insertIfKeyIsNew(any())).thenReturn(0);
		when(transfers.findByIdempotencyKey("key-1")).thenReturn(Optional.of(first));

		assertThat(ledger.transfer(alice, bob, 500, "key-1")).isSameAs(first);

		verify(accounts, never()).debit(any(), anyLong());
		verifyNoInteractions(entries);
	}

	@Test
	void aKeyReusedForADifferentTransferIsRejected() {
		bothInEuros();
		when(transfers.insertIfKeyIsNew(any())).thenReturn(0);
		when(transfers.findByIdempotencyKey("key-1")).thenReturn(Optional.of(new Transfer(alice, bob, 500, "key-1")));

		assertThatThrownBy(() -> ledger.transfer(alice, bob, 900, "key-1"))
			.isInstanceOf(IdempotencyKeyReusedException.class);

		verify(accounts, never()).debit(any(), anyLong());
	}

	@Test
	void accountsInDifferentCurrenciesCannotTransfer() {
		when(accounts.findById(alice)).thenReturn(Optional.of(new Account("Alice", "EUR")));
		when(accounts.findById(bob)).thenReturn(Optional.of(new Account("Bob", "USD")));

		assertThatThrownBy(() -> ledger.transfer(alice, bob, 500, "key-1"))
			.isInstanceOf(IllegalArgumentException.class);

		verifyNoInteractions(transfers);
	}

	@Test
	void anAccountCannotTransferToItself() {
		assertThatThrownBy(() -> ledger.transfer(alice, alice, 500, "key-1"))
			.isInstanceOf(IllegalArgumentException.class);

		verifyNoInteractions(accounts, transfers);
	}

	@Test
	void anUnknownAccountIsNotFound() {
		when(accounts.findById(alice)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> ledger.transfer(alice, bob, 500, "key-1"))
			.isInstanceOf(NoSuchElementException.class);
	}

	private void bothInEuros() {
		when(accounts.findById(alice)).thenReturn(Optional.of(new Account("Alice", "EUR")));
		when(accounts.findById(bob)).thenReturn(Optional.of(new Account("Bob", "EUR")));
	}

}
