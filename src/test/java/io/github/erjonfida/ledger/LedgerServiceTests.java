package io.github.erjonfida.ledger;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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
		when(accounts.findById(alice)).thenReturn(Optional.of(new Account("Alice", "EUR")));
		when(accounts.findById(bob)).thenReturn(Optional.of(new Account("Bob", "EUR")));
		when(accounts.debit(alice, 500)).thenReturn(1);
		when(transfers.save(any())).thenAnswer(returnsFirstArg());

		ledger.transfer(alice, bob, 500);

		verify(accounts).credit(bob, 500);
		ArgumentCaptor<Entry> written = ArgumentCaptor.forClass(Entry.class);
		verify(entries, times(2)).save(written.capture());
		assertThat(written.getAllValues()).extracting(Entry::getAccountId).containsExactly(alice, bob);
		assertThat(written.getAllValues()).extracting(Entry::getAmount).containsExactly(-500L, 500L);
	}

	@Test
	void aTransferOverTheBalanceWritesNothing() {
		when(accounts.findById(alice)).thenReturn(Optional.of(new Account("Alice", "EUR")));
		when(accounts.findById(bob)).thenReturn(Optional.of(new Account("Bob", "EUR")));
		when(accounts.debit(alice, 500)).thenReturn(0);

		assertThatThrownBy(() -> ledger.transfer(alice, bob, 500)).isInstanceOf(InsufficientFundsException.class);

		verify(accounts, never()).credit(any(), anyLong());
		verifyNoInteractions(transfers, entries);
	}

	@Test
	void accountsInDifferentCurrenciesCannotTransfer() {
		when(accounts.findById(alice)).thenReturn(Optional.of(new Account("Alice", "EUR")));
		when(accounts.findById(bob)).thenReturn(Optional.of(new Account("Bob", "USD")));

		assertThatThrownBy(() -> ledger.transfer(alice, bob, 500)).isInstanceOf(IllegalArgumentException.class);

		verify(accounts, never()).debit(any(), anyLong());
	}

	@Test
	void anAccountCannotTransferToItself() {
		assertThatThrownBy(() -> ledger.transfer(alice, alice, 500)).isInstanceOf(IllegalArgumentException.class);

		verifyNoInteractions(accounts);
	}

	@Test
	void anUnknownAccountIsNotFound() {
		when(accounts.findById(alice)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> ledger.transfer(alice, bob, 500)).isInstanceOf(NoSuchElementException.class);
	}

}
