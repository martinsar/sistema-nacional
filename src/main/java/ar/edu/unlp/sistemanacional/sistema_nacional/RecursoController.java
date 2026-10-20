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
	public List<RecursoService.RecursoResponse> findAll() {
		return recursoService.findAll();
	}

	@GetMapping("/{id}")
	public RecursoService.RecursoResponse findById(@PathVariable Long id) {
		return recursoService.findById(id);
	}

	@PostMapping
	public ResponseEntity<RecursoService.RecursoResponse> create(
			@RequestBody RecursoService.RecursoRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(recursoService.create(request));
	}

	@PutMapping("/{id}")
	public RecursoService.RecursoResponse update(@PathVariable Long id,
			@RequestBody RecursoService.RecursoRequest request) {
		return recursoService.update(id, request);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable Long id) {
		recursoService.delete(id);
		return ResponseEntity.noContent().build();
	}
}
