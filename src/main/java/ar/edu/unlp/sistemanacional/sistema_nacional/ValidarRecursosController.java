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

    public ValidarRecursosController(ValidarRecursosService validarRecursosService) {
        this.validarRecursoService = validarRecursosService;
    }

    @PostMapping("/recursos")
    public ResponseEntity<List<Oferta>> validarRecursos(
            @RequestBody List<Oferta> recursosEntrantes) {
        
        List<Oferta> resultados = validarRecursoService.validarOfertas(recursosEntrantes);
        return ResponseEntity.ok(resultados);
    }



}