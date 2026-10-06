package ar.edu.unlp.sistemanacional.sistema_nacional;

import org.springframework.stereotype.Service;

@Service
public class TestService {
	public String authenticatedMessage(String username) {
		return "JWT válido para " + username;
	}
}