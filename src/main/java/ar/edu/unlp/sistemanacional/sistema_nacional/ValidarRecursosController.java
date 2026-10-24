package ar.edu.unlp.sistemanacional.sistema_nacional;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
 

@RestController
@RequestMapping("/api/validar")
public class ValidarRecursosController {

    private final ValidarRecursosService validarRecursoService;
    private final DescontarRecursosService descontarRecursosService;

    public ValidarRecursosController(
            ValidarRecursosService validarRecursosService,
            DescontarRecursosService descontarRecursosService) {
        this.validarRecursoService = validarRecursosService;
        this.descontarRecursosService = descontarRecursosService;
    }

    @PostMapping("/recursos")
    public ResponseEntity<List<Oferta>> validarRecursos(
            @RequestBody List<Oferta> recursosEntrantes) {
        
        List<Oferta> resultados = validarRecursoService.validarOfertas(recursosEntrantes);
        return ResponseEntity.ok(resultados);
    }

    @PostMapping("/coincidencias")
    public ResponseEntity<DescontarRecursosService.DescuentoResponse> descontarRecursos(
            @RequestBody DescontarRecursosService.SolicitudDescuento solicitud) {
        return ResponseEntity.ok(descontarRecursosService.descontar(solicitud));
    }

}