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
@RequestMapping("/api/ongs")
@SecurityRequirement(name = "bearerAuth")
public class OngController {

	private final OngService ongService;

	public OngController(OngService ongService) {
		this.ongService = ongService;
	}

	@GetMapping
	@Operation(summary = "Listar ONGs", description = "Devuelve todas las ONGs y sus tipos de recursos habilitados. La respuesta incluye el id numérico interno y el codigo_ong estable de cada ONG. Para identificar una ONG entre sistemas se debe utilizar codigo_ong. Requiere autenticación JWT y no requiere cuerpo.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Lista de ONGs obtenida."),
			@ApiResponse(responseCode = "401", description = "Falta el token JWT o no es válido.")
	})
	public List<OngService.OngResponse> findAll() {
		return ongService.findAll();
	}

	@GetMapping("/{id}")
	@Operation(summary = "Consultar ONG por ID interno (ruta heredada)", description = "Esta ruta utiliza el id numérico interno incluido en la respuesta de GET /api/ongs. Por ejemplo, para consultar la ONG con id 3, la URL es GET /api/ongs/3. El id solo es válido dentro de esta base de datos. Para consultas entre sistemas se debe utilizar GET /api/ongs/codigo/APP-ONG-123 con el codigo_ong correspondiente. Requiere autenticación JWT.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "ONG encontrada.",
					content = @Content(schema = @Schema(implementation = OngService.OngResponse.class))),
			@ApiResponse(responseCode = "401", description = "Falta el token JWT o no es válido."),
			@ApiResponse(responseCode = "404", description = "No existe una ONG con ese identificador.",
					content = @Content(schema = @Schema(implementation = ApiExceptionHandler.ErrorResponse.class)))
	})
	public OngService.OngResponse findById(
			@Parameter(description = "ID numérico interno de esta base de datos; no usar para identificar la ONG entre sistemas.", required = true) @PathVariable Long id) {
		return ongService.findById(id);
	}

	@GetMapping("/codigo/{codigo_ong}")
	@Operation(summary = "Consultar ONG por código estable", description = "Busca una ONG por su identificador compartido. La ruta de ejemplo es GET /api/ongs/codigo/APP-ONG-123; APP-ONG-123 debe corresponder al codigo_ong asignado a la ONG. No requiere cuerpo ni el id numérico interno. Requiere autenticación JWT.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "ONG encontrada.",
					content = @Content(schema = @Schema(implementation = OngService.OngResponse.class))),
			@ApiResponse(responseCode = "401", description = "Falta el token JWT o no es válido."),
			@ApiResponse(responseCode = "404", description = "No existe una ONG con ese codigo_ong.",
					content = @Content(schema = @Schema(implementation = ApiExceptionHandler.ErrorResponse.class)))
	})
	public OngService.OngResponse findByCodigoOng(
			@Parameter(description = "Código estable compartido de la ONG, por ejemplo APP-ONG-123.", required = true)
			@PathVariable("codigo_ong") String codigoOng) {
		return ongService.findByCodigoOng(codigoOng);
	}

	@PostMapping
	@Operation(summary = "Crear ONG", description = "Registra una ONG y devuelve sus identificadores. El cuerpo debe incluir nombre y al menos un tipo en tiposRecursos. Ejemplo: {\"nombre\":\"Caritas\",\"tiposRecursos\":[\"Alimentos\"],\"codigo_ong\":\"APP-ONG-123\"}. codigo_ong es opcional: si no se proporciona, esta API genera uno. Para integraciones posteriores se debe utilizar el codigo_ong devuelto; el id numérico es interno. Requiere autenticación JWT.")
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "ONG creada.",
					content = @Content(schema = @Schema(implementation = OngService.OngResponse.class))),
			@ApiResponse(responseCode = "400", description = "El nombre es obligatorio (máximo 150 caracteres), debe incluir al menos un tipo de recurso y el codigo_ong, si se envía, debe ser válido.",
					content = @Content(schema = @Schema(implementation = ApiExceptionHandler.ErrorResponse.class))),
			@ApiResponse(responseCode = "401", description = "Falta el token JWT o no es válido."),
			@ApiResponse(responseCode = "409", description = "El codigo_ong ya está asignado a otra ONG.",
					content = @Content(schema = @Schema(implementation = ApiExceptionHandler.ErrorResponse.class)))
	})
	public ResponseEntity<OngService.OngResponse> create(@RequestBody OngService.OngRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(ongService.create(request));
	}

	@PutMapping("/{id}")
	@Operation(summary = "Actualizar ONG por ID interno (ruta heredada)", description = "Reemplaza el nombre y los tipos habilitados de una ONG. El parámetro id corresponde al valor numérico interno incluido en GET /api/ongs. Por ejemplo, para actualizar la ONG con id 3, la URL es PUT /api/ongs/3. Este identificador solo es válido dentro de esta base. Para integraciones entre sistemas se debe utilizar PUT /api/ongs/codigo/APP-ONG-123. El cuerpo debe incluir nombre y tiposRecursos; codigo_ong no se puede modificar. Requiere autenticación JWT.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "ONG actualizada.",
					content = @Content(schema = @Schema(implementation = OngService.OngResponse.class))),
			@ApiResponse(responseCode = "400", description = "El nombre es obligatorio (máximo 150 caracteres), debe incluir al menos un tipo de recurso y no se puede cambiar el codigo_ong.",
					content = @Content(schema = @Schema(implementation = ApiExceptionHandler.ErrorResponse.class))),
			@ApiResponse(responseCode = "401", description = "Falta el token JWT o no es válido."),
			@ApiResponse(responseCode = "404", description = "No existe una ONG con ese identificador.",
					content = @Content(schema = @Schema(implementation = ApiExceptionHandler.ErrorResponse.class)))
	})
	public OngService.OngResponse update(
			@Parameter(description = "ID numérico interno de esta base; no es el identificador compartido entre sistemas.", required = true) @PathVariable Long id,
			@RequestBody OngService.OngRequest request) {
		return ongService.update(id, request);
	}

	@PutMapping("/codigo/{codigo_ong}")
	@Operation(summary = "Actualizar ONG por código estable", description = "Reemplaza el nombre y los tipos de recursos habilitados. La ruta de ejemplo es PUT /api/ongs/codigo/APP-ONG-123; APP-ONG-123 debe corresponder al codigo_ong de la ONG. El cuerpo debe incluir nombre y tiposRecursos. Ejemplo: {\"nombre\":\"Caritas\",\"tiposRecursos\":[\"Alimentos\",\"Medicamentos\"]}. codigo_ong es estable y no se modifica. Requiere autenticación JWT.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "ONG actualizada.",
					content = @Content(schema = @Schema(implementation = OngService.OngResponse.class))),
			@ApiResponse(responseCode = "400", description = "Los datos son inválidos o se intentó cambiar codigo_ong.",
					content = @Content(schema = @Schema(implementation = ApiExceptionHandler.ErrorResponse.class))),
			@ApiResponse(responseCode = "401", description = "Falta el token JWT o no es válido."),
			@ApiResponse(responseCode = "404", description = "No existe una ONG con ese codigo_ong.",
					content = @Content(schema = @Schema(implementation = ApiExceptionHandler.ErrorResponse.class)))
	})
	public OngService.OngResponse updateByCodigoOng(
			@Parameter(description = "Código estable compartido de la ONG, por ejemplo APP-ONG-123.", required = true)
			@PathVariable("codigo_ong") String codigoOng,
			@RequestBody OngService.OngRequest request) {
		return ongService.updateByCodigoOng(codigoOng, request);
	}

	@DeleteMapping("/{id}")
	@Operation(summary = "Eliminar ONG por ID interno (ruta heredada)", description = "Elimina una ONG si no tiene recursos asociados. El parámetro id corresponde al valor numérico interno incluido en GET /api/ongs. Por ejemplo, para eliminar la ONG con id 3, la URL es DELETE /api/ongs/3. Este identificador solo es válido dentro de esta base. Para integraciones entre sistemas se debe utilizar DELETE /api/ongs/codigo/APP-ONG-123. No requiere cuerpo. Requiere autenticación JWT.")
	@ApiResponses({
			@ApiResponse(responseCode = "204", description = "ONG eliminada."),
			@ApiResponse(responseCode = "401", description = "Falta el token JWT o no es válido."),
			@ApiResponse(responseCode = "404", description = "No existe una ONG con ese identificador.",
					content = @Content(schema = @Schema(implementation = ApiExceptionHandler.ErrorResponse.class))),
			@ApiResponse(responseCode = "409", description = "No se puede eliminar la ONG porque tiene recursos asociados.",
					content = @Content(schema = @Schema(implementation = ApiExceptionHandler.ErrorResponse.class)))
	})
	public ResponseEntity<Void> delete(
			@Parameter(description = "ID numérico interno de esta base; no es el identificador compartido entre sistemas.", required = true) @PathVariable Long id) {
		ongService.delete(id);
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping("/codigo/{codigo_ong}")
	@Operation(summary = "Eliminar ONG por código estable", description = "Elimina la ONG identificada por codigo_ong, siempre que no tenga recursos asociados. La ruta de ejemplo es DELETE /api/ongs/codigo/APP-ONG-123; APP-ONG-123 debe corresponder al código de la ONG. No requiere cuerpo. Requiere autenticación JWT.")
	@ApiResponses({
			@ApiResponse(responseCode = "204", description = "ONG eliminada."),
			@ApiResponse(responseCode = "401", description = "Falta el token JWT o no es válido."),
			@ApiResponse(responseCode = "404", description = "No existe una ONG con ese codigo_ong.",
					content = @Content(schema = @Schema(implementation = ApiExceptionHandler.ErrorResponse.class))),
			@ApiResponse(responseCode = "409", description = "No se puede eliminar la ONG porque tiene recursos asociados.",
					content = @Content(schema = @Schema(implementation = ApiExceptionHandler.ErrorResponse.class)))
	})
	public ResponseEntity<Void> deleteByCodigoOng(
			@Parameter(description = "Código estable compartido de la ONG, por ejemplo APP-ONG-123.", required = true)
			@PathVariable("codigo_ong") String codigoOng) {
		ongService.deleteByCodigoOng(codigoOng);
		return ResponseEntity.noContent().build();
	}
}
