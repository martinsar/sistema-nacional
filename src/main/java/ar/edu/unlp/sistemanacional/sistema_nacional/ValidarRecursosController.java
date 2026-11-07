package ar.edu.unlp.sistemanacional.sistema_nacional;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@RequestMapping("/api")
@SecurityRequirement(name = "bearerAuth")
public class ValidarRecursosController {

    private final ValidarRecursosService validarRecursoService;
    private final DescontarRecursosService descontarRecursosService;
    private final LiberarRecursosService liberarRecursosService;

    public ValidarRecursosController(
            ValidarRecursosService validarRecursosService,
            DescontarRecursosService descontarRecursosService,
            LiberarRecursosService liberarRecursosService) {
        this.validarRecursoService = validarRecursosService;
        this.descontarRecursosService = descontarRecursosService;
        this.liberarRecursosService = liberarRecursosService;
    }

    @PostMapping({"/ofertas/validar", "/validar/recursos"})
    @Operation(summary = "Validar ofertas",
            description = "Requiere JWT. Envía una lista JSON a POST /api/ofertas/validar (alias compatible: /api/validar/recursos). Cada elemento lleva codigo_ong, recurso_tipo y recurso_cantidad; ejemplo: [{\"codigo_ong\":\"APP-ONG-123\",\"recurso_tipo\":\"Alimentos\",\"recurso_cantidad\":5}]. La respuesta devuelve la misma lista con habilitado_flag=true si la ONG está habilitada para ese tipo, o false si no. No modifica el stock. organizacion_nombre se acepta temporalmente para clientes anteriores; prioriza codigo_ong.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ofertas procesadas; cada una incluye habilitado_flag.",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = Oferta.class)))),
            @ApiResponse(responseCode = "400", description = "El cuerpo está ausente o no contiene una lista JSON válida."),
            @ApiResponse(responseCode = "401", description = "Falta el token JWT o no es válido.")
    })
    public ResponseEntity<List<Oferta>> validarRecursos(
            @RequestBody List<Oferta> recursosEntrantes) {

        List<Oferta> resultados = validarRecursoService.validarOfertas(recursosEntrantes);
        return ResponseEntity.ok(resultados);
    }

    @PostMapping({"/recursos/comprometer", "/validar/coincidencias"})
    @Operation(summary = "Comprometer recursos",
            description = "Requiere JWT. Envía POST /api/recursos/comprometer (alias compatible: /api/validar/coincidencias) con JSON que incluya tipo_recurso, codigo_ong, cantidad positiva e id_convocatoria. Ejemplo: {\"tipo_recurso\":\"Alimentos\",\"codigo_ong\":\"APP-ONG-123\",\"cantidad\":5,\"id_convocatoria\":10}. Busca recursos disponibles de esa ONG y tipo, los bloquea y registra adjudicaciones para liberarlos luego. La operación es atómica: compromete todo lo pedido o nada. nombre_ong se acepta temporalmente para compatibilidad; prioriza codigo_ong.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "La cantidad solicitada fue comprometida.",
                    content = @Content(schema = @Schema(implementation = DescontarRecursosService.DescuentoResponse.class))),
            @ApiResponse(responseCode = "400", description = "Falta el tipo, codigo_ong (o nombre_ong legado), id de convocatoria o una cantidad mayor a cero.",
                    content = @Content(schema = @Schema(implementation = ApiExceptionHandler.ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Falta el token JWT o no es válido."),
            @ApiResponse(responseCode = "404", description = "La ONG no tiene recursos del tipo solicitado.",
                    content = @Content(schema = @Schema(implementation = ApiExceptionHandler.ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "La disponibilidad no alcanza; no se compromete ninguna unidad.",
                    content = @Content(schema = @Schema(implementation = ApiExceptionHandler.ErrorResponse.class)))
    })
    public ResponseEntity<DescontarRecursosService.DescuentoResponse> descontarRecursos(
            @RequestBody DescontarRecursosService.SolicitudDescuento solicitud) {
        return ResponseEntity.ok(descontarRecursosService.descontar(solicitud));
    }

    @PostMapping("/convocatorias/{id}/recursos/liberar")
    @Operation(summary = "Liberar recursos de una convocatoria",
            description = "Libera los recursos comprometidos en una convocatoria. El parámetro id corresponde al número de la convocatoria. Por ejemplo, para la convocatoria 10, la URL es POST /api/convocatorias/10/recursos/liberar. No requiere cuerpo y requiere autenticación JWT. Si alguna cantidad no puede liberarse, procesa las restantes e informa la situación.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Se liberaron las adjudicaciones pendientes.",
                    content = @Content(schema = @Schema(implementation = LiberarRecursosService.LiberacionResponse.class))),
            @ApiResponse(responseCode = "400", description = "El id de la convocatoria no es válido.",
                    content = @Content(schema = @Schema(implementation = ApiExceptionHandler.ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Falta el token JWT o no es válido."),
            @ApiResponse(responseCode = "404", description = "La convocatoria no tiene adjudicaciones pendientes.",
                    content = @Content(schema = @Schema(implementation = ApiExceptionHandler.ErrorResponse.class)))
    })
    public ResponseEntity<LiberarRecursosService.LiberacionResponse> liberarRecursos(
            @Parameter(description = "Identificador de la convocatoria", required = true)
            @PathVariable Long id) {
        return liberarRecursosPorSolicitud(new LiberarRecursosService.SolicitudLiberacion(id));
    }

    @PostMapping("/validar/liberar")
    @Operation(summary = "Liberar recursos (ruta compatible)",
            description = "Libera los recursos comprometidos para una convocatoria. El cuerpo debe incluir id_convocatoria. Ejemplo: {\"id_convocatoria\":10}. Requiere autenticación JWT. La ruta recomendada para nuevas integraciones es POST /api/convocatorias/10/recursos/liberar, donde 10 representa el número de la convocatoria; esta ruta no requiere cuerpo.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Se liberaron las adjudicaciones pendientes.",
                    content = @Content(schema = @Schema(implementation = LiberarRecursosService.LiberacionResponse.class))),
            @ApiResponse(responseCode = "400", description = "Falta id_convocatoria o el cuerpo no es válido.",
                    content = @Content(schema = @Schema(implementation = ApiExceptionHandler.ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Falta el token JWT o no es válido."),
            @ApiResponse(responseCode = "404", description = "La convocatoria no tiene adjudicaciones pendientes.",
                    content = @Content(schema = @Schema(implementation = ApiExceptionHandler.ErrorResponse.class)))
    })
    public ResponseEntity<LiberarRecursosService.LiberacionResponse> liberarRecursos(
            @RequestBody(required = false) LiberarRecursosService.SolicitudLiberacion solicitud) {
        if (solicitud == null || solicitud.idConvocatoria() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El id de la convocatoria es obligatorio");
        }

        return liberarRecursosPorSolicitud(solicitud);
    }

    private ResponseEntity<LiberarRecursosService.LiberacionResponse> liberarRecursosPorSolicitud(
            LiberarRecursosService.SolicitudLiberacion solicitud) {
        return ResponseEntity.ok(liberarRecursosService.liberar(solicitud));
    }

}