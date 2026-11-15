package ar.edu.unlp.sistemanacional.sistema_nacional;

import java.util.Arrays;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@RequestMapping("/api/tipos-recurso")
@SecurityRequirement(name = "bearerAuth")
public class TipoRecursoController {

	@GetMapping
	@Operation(summary = "Listar el catálogo de tipos de recurso", description = "Devuelve el catálogo único de tipos de recurso definido por el Sistema Nacional. El codigo es estable y es el valor que se debe usar en tiposRecursos de las ONGs, en el tipo de los recursos y en recurso_tipo de las ofertas. Requiere autenticación JWT y no requiere cuerpo.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Catálogo de tipos de recurso.",
					content = @Content(array = @ArraySchema(schema = @Schema(implementation = TipoRecursoResponse.class)),
							examples = @ExampleObject(value = "[{\"codigo\":\"ALIMENTOS\",\"nombre\":\"Raciones de alimento\"},{\"codigo\":\"AGUA\",\"nombre\":\"Agua potable\"}]"))),
			@ApiResponse(responseCode = "401", description = "Falta el token JWT o no es válido.")
	})
	public List<TipoRecursoResponse> findAll() {
		return Arrays.stream(TipoRecurso.values())
				.map(t -> new TipoRecursoResponse(t.getCodigo(), t.getNombre())).toList();
	}

	public record TipoRecursoResponse(String codigo, String nombre) {
	}
}
