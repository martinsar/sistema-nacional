package ar.edu.unlp.sistemanacional.sistema_nacional;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/test")
@SecurityRequirement(name = "bearerAuth")
public class TestController {

	private final TestService testService;

	public TestController(TestService testService) {
		this.testService = testService;
	}

	@GetMapping("/protected")
	public TestResponse protectedEndpoint(Authentication authentication) {
		return new TestResponse(testService.authenticatedMessage(authentication.getName()), authentication.getName());
	}

	public record TestResponse(String message, String username) {
	}
}