package io.github.erjonfida.ledger;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/clients")
class ClientController {

	record RegisterRequest(@NotBlank @Size(max = 100) String name) {
	}

	record RegisteredClient(UUID id, String name, String apiKey) {
	}

	private final ClientRepository clients;

	ClientController(ClientRepository clients) {
		this.clients = clients;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	RegisteredClient register(@Valid @RequestBody RegisterRequest request) {
		String apiKey = Client.newApiKey();
		Client client = clients.save(new Client(request.name(), apiKey));
		return new RegisteredClient(client.getId(), client.getName(), apiKey);
	}

}
