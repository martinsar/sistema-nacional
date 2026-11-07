package ar.edu.unlp.sistemanacional.sistema_nacional;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
	@Operation(summary = "Probar autenticación", description = "Requiere una solicitud GET con el encabezado Authorization: Bearer <accessToken>. El token también puede configurarse mediante el botón Authorize de Swagger. Si es válido, la respuesta incluye un mensaje y el nombre del usuario autenticado.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Token válido; usuario autenticado."),
			@ApiResponse(responseCode = "401", description = "Falta el token JWT o no es válido.")
	})
	public TestResponse protectedEndpoint(Authentication authentication) {
		return new TestResponse(testService.authenticatedMessage(authentication.getName()), authentication.getName());
	}

	public record TestResponse(String message, String username) {
	}
}