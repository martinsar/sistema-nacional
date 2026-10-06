package ar.edu.unlp.sistemanacional.sistema_nacional;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@Transactional
	public void register(String username, String password) {
		if (username == null || username.isBlank() || username.length() > 100) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username is required and must be at most 100 characters");
		}
		if (password == null || password.length() < 8) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must contain at least 8 characters");
		}

		String normalizedUsername = username.trim();
		if (userRepository.existsByUsername(normalizedUsername)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
		}
		userRepository.save(new ApplicationUser(normalizedUsername, passwordEncoder.encode(password)));
	}
}