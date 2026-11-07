package ar.edu.unlp.sistemanacional.sistema_nacional;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@RequestMapping("/api/recursos")
@SecurityRequirement(name = "bearerAuth")
public class RecursoController {

	private final RecursoService recursoService;

	public RecursoController(RecursoService recursoService) {
		this.recursoService = recursoService;
	}

	@GetMapping
	@Operation(summary = "Listar recursos", description = "Requiere JWT. Envía GET /api/recursos sin cuerpo para consultar el stock total, bloqueado y disponible de cada recurso, junto con la ONG asociada.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Lista de recursos obtenida."),
			@ApiResponse(responseCode = "401", description = "Falta el token JWT o no es válido.")
	})
	public List<RecursoService.RecursoResponse> findAll() {
		return recursoService.findAll();
	}

	@GetMapping("/{id}")
	@Operation(summary = "Consultar recurso", description = "Devuelve los datos y las cantidades de un recurso. El parámetro id corresponde al identificador numérico interno del recurso, incluido en la respuesta de GET /api/recursos. Por ejemplo, para consultar el recurso con id 8, la URL es GET /api/recursos/8. Este identificador no es el codigo_ong. No requiere cuerpo y requiere autenticación JWT.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Recurso encontrado.",
					content = @Content(schema = @Schema(implementation = RecursoService.RecursoResponse.class))),
			@ApiResponse(responseCode = "401", description = "Falta el token JWT o no es válido."),
			@ApiResponse(responseCode = "404", description = "No existe un recurso con ese identificador.",
					content = @Content(schema = @Schema(implementation = ApiExceptionHandler.ErrorResponse.class)))
	})
	public RecursoService.RecursoResponse findById(
			@Parameter(description = "Identificador del recurso", required = true) @PathVariable Long id) {
		return recursoService.findById(id);
	}

	@PostMapping
	@Operation(summary = "Crear recurso", description = "Registra stock y lo asocia a una ONG existente. La solicitud debe incluir tipo, cantidadTotal, cantidadBloqueada y codigo_ong. Ejemplo: {\"tipo\":\"Alimentos\",\"cantidadTotal\":100,\"cantidadBloqueada\":0,\"codigo_ong\":\"APP-ONG-123\"}. codigo_ong es el identificador estable de la ONG; cantidadBloqueada no puede superar cantidadTotal. ongId numérico se mantiene por compatibilidad. Requiere autenticación JWT.")
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "Recurso creado.",
					content = @Content(schema = @Schema(implementation = RecursoService.RecursoResponse.class))),
			@ApiResponse(responseCode = "400", description = "Faltan datos, las cantidades son inválidas o falta el id de la ONG.",
					content = @Content(schema = @Schema(implementation = ApiExceptionHandler.ErrorResponse.class))),
			@ApiResponse(responseCode = "401", description = "Falta el token JWT o no es válido."),
			@ApiResponse(responseCode = "404", description = "No existe la ONG indicada.",
					content = @Content(schema = @Schema(implementation = ApiExceptionHandler.ErrorResponse.class)))
	})
	public ResponseEntity<RecursoService.RecursoResponse> create(
			@RequestBody RecursoService.RecursoRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(recursoService.create(request));
	}

	@PutMapping("/{id}")
	@Operation(summary = "Actualizar recurso", description = "Reemplaza el tipo, las cantidades y la ONG asociada. El parámetro id corresponde al identificador numérico interno incluido en GET /api/recursos. Por ejemplo, para actualizar el recurso con id 8, la URL es PUT /api/recursos/8. El cuerpo debe incluir tipo, cantidadTotal, cantidadBloqueada y codigo_ong de la ONG. cantidadBloqueada no puede superar cantidadTotal. ongId numérico se mantiene por compatibilidad. Requiere autenticación JWT.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Recurso actualizado.",
					content = @Content(schema = @Schema(implementation = RecursoService.RecursoResponse.class))),
			@ApiResponse(responseCode = "400", description = "Faltan datos, las cantidades son inválidas o falta el id de la ONG.",
					content = @Content(schema = @Schema(implementation = ApiExceptionHandler.ErrorResponse.class))),
			@ApiResponse(responseCode = "401", description = "Falta el token JWT o no es válido."),
			@ApiResponse(responseCode = "404", description = "No existe el recurso o la ONG indicada.",
					content = @Content(schema = @Schema(implementation = ApiExceptionHandler.ErrorResponse.class)))
	})
	public RecursoService.RecursoResponse update(@PathVariable Long id,
			@RequestBody RecursoService.RecursoRequest request) {
		return recursoService.update(id, request);
	}

	@DeleteMapping("/{id}")
	@Operation(summary = "Eliminar recurso", description = "Elimina el recurso si no tiene adjudicaciones registradas. El parámetro id corresponde al identificador numérico interno incluido en GET /api/recursos. Por ejemplo, para eliminar el recurso con id 8, la URL es DELETE /api/recursos/8. No requiere cuerpo. Si existen adjudicaciones, responde 409 para conservar el historial. Requiere autenticación JWT.")
	@ApiResponses({
			@ApiResponse(responseCode = "204", description = "Recurso eliminado."),
			@ApiResponse(responseCode = "401", description = "Falta el token JWT o no es válido."),
			@ApiResponse(responseCode = "409", description = "El recurso tiene adjudicaciones registradas y no puede eliminarse.",
					content = @Content(schema = @Schema(implementation = ApiExceptionHandler.ErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "No existe un recurso con ese identificador.",
					content = @Content(schema = @Schema(implementation = ApiExceptionHandler.ErrorResponse.class)))
	})
	public ResponseEntity<Void> delete(
			@Parameter(description = "Identificador del recurso", required = true) @PathVariable Long id) {
		recursoService.delete(id);
		return ResponseEntity.noContent().build();
	}
}
