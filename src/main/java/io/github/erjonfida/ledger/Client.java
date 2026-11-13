package io.github.erjonfida.ledger;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Client {

	private static final SecureRandom RANDOM = new SecureRandom();

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	private String name;

	private String apiKeyHash;

	protected Client() {
	}

	Client(String name, String apiKey) {
		this.name = name;
		this.apiKeyHash = hash(apiKey);
	}

	static String newApiKey() {
		byte[] key = new byte[32];
		RANDOM.nextBytes(key);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(key);
	}

	static String hash(String apiKey) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(apiKey.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	public UUID getId() {
		return id;
	}

	public String getName() {
		return name;
	}

}
