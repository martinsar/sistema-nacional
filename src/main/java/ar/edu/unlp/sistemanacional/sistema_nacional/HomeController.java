package ar.edu.unlp.sistemanacional.sistema_nacional;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HomeController {

	@GetMapping("/")
	public ApiStatus status() {
		return new ApiStatus("Sistema Nacional API", "running", "/api/auth/login");
	}

	public record ApiStatus(String application, String status, String loginEndpoint) {
	}
}