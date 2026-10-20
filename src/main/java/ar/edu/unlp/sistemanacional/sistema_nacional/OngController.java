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
@RequestMapping("/api/ongs")
@SecurityRequirement(name = "bearerAuth")
public class OngController {

	private final OngService ongService;

	public OngController(OngService ongService) {
		this.ongService = ongService;
	}

	@GetMapping
	public List<OngService.OngResponse> findAll() {
		return ongService.findAll();
	}

	@GetMapping("/{id}")
	public OngService.OngResponse findById(@PathVariable Long id) {
		return ongService.findById(id);
	}

	@PostMapping
	public ResponseEntity<OngService.OngResponse> create(@RequestBody OngService.OngRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(ongService.create(request));
	}

	@PutMapping("/{id}")
	public OngService.OngResponse update(@PathVariable Long id, @RequestBody OngService.OngRequest request) {
		return ongService.update(id, request);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable Long id) {
		ongService.delete(id);
		return ResponseEntity.noContent().build();
	}
}
