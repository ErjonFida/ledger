package io.github.erjonfida.ledger;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
class LedgerController {

	record OpenAccountRequest(@NotBlank @Size(max = 100) String name,
			@NotNull @Pattern(regexp = "[A-Z]{3}") String currency) {
	}

	record AccountView(UUID id, String name, String currency, long balance) {

		static AccountView of(Account a) {
			return new AccountView(a.getId(), a.getName(), a.getCurrency(), a.getBalance());
		}

	}

	record DepositRequest(@Positive long amount) {
	}

	record TransferRequest(@NotNull UUID fromAccountId, @NotNull UUID toAccountId, @Positive long amount) {
	}

	record TransferView(UUID id, UUID fromAccountId, UUID toAccountId, long amount, Instant createdAt) {

		static TransferView of(Transfer t) {
			return new TransferView(t.getId(), t.getFromAccountId(), t.getToAccountId(), t.getAmount(),
					t.getCreatedAt());
		}

	}

	record EntryView(UUID transferId, long amount, Instant createdAt) {

		static EntryView of(Entry e) {
			return new EntryView(e.getTransferId(), e.getAmount(), e.getCreatedAt());
		}

	}

	private final LedgerService ledger;

	LedgerController(LedgerService ledger) {
		this.ledger = ledger;
	}

	@PostMapping("/accounts")
	@ResponseStatus(HttpStatus.CREATED)
	AccountView open(@Valid @RequestBody OpenAccountRequest request) {
		return AccountView.of(ledger.open(request.name(), request.currency()));
	}

	@GetMapping("/accounts/{id}")
	AccountView account(@PathVariable UUID id) {
		return AccountView.of(ledger.account(id));
	}

	@GetMapping("/accounts/{id}/entries")
	List<EntryView> entries(@PathVariable UUID id, @RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
		return ledger.entries(id, page, size).stream().map(EntryView::of).toList();
	}

	@PostMapping("/accounts/{id}/deposits")
	@ResponseStatus(HttpStatus.CREATED)
	TransferView deposit(@PathVariable UUID id, @Valid @RequestBody DepositRequest request) {
		return TransferView.of(ledger.deposit(id, request.amount()));
	}

	@PostMapping("/transfers")
	@ResponseStatus(HttpStatus.CREATED)
	TransferView transfer(@Valid @RequestBody TransferRequest request) {
		return TransferView.of(ledger.transfer(request.fromAccountId(), request.toAccountId(), request.amount()));
	}

	@ExceptionHandler
	ProblemDetail notFound(NoSuchElementException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
	}

	@ExceptionHandler
	ProblemDetail insufficientFunds(InsufficientFundsException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
	}

	@ExceptionHandler
	ProblemDetail badRequest(IllegalArgumentException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
	}

}
