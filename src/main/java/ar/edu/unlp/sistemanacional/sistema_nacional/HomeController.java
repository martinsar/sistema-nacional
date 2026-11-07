package ar.edu.unlp.sistemanacional.sistema_nacional;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

@RestController
public class HomeController {

	@GetMapping("/")
	@Operation(summary = "Estado de la API", description = "Consulta pública, sin token. Úsala para comprobar que la API está activa; la respuesta incluye el estado y la ruta para iniciar sesión.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Estado actual de la API.",
					content = @Content(schema = @Schema(implementation = ApiStatus.class)))
	})
	public ApiStatus status() {
		return new ApiStatus("Sistema Nacional API", "running", "/api/auth/login");
	}

	public record ApiStatus(String application, String status, String loginEndpoint) {
	}
}