package ar.edu.unlp.sistemanacional.sistema_nacional;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class OngService {

	private final OngRepository ongRepository;
	private final RecursoRepository recursoRepository;

	public OngService(OngRepository ongRepository, RecursoRepository recursoRepository) {
		this.ongRepository = ongRepository;
		this.recursoRepository = recursoRepository;
	}

	@Transactional(readOnly = true)
	public List<OngResponse> findAll() {
		return ongRepository.findAll().stream().map(OngService::toResponse).toList();
	}

	@Transactional(readOnly = true)
	public OngResponse findById(Long id) {
		return toResponse(getOng(id));
	}

	public OngResponse create(OngRequest request) {
		validate(request);
		return toResponse(ongRepository.save(new Ong(request.nombre().trim(), request.tipo().trim())));
	}

	public OngResponse update(Long id, OngRequest request) {
		validate(request);
		Ong ong = getOng(id);
		ong.setNombre(request.nombre().trim());
		ong.setTipo(request.tipo().trim());
		return toResponse(ongRepository.save(ong));
	}

	public void delete(Long id) {
		Ong ong = getOng(id);
		if (recursoRepository.existsByOngId(id)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "No se puede eliminar una ONG que tiene recursos");
		}
		ongRepository.delete(ong);
	}

	private Ong getOng(Long id) {
		return ongRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ONG no encontrada"));
	}

	private static void validate(OngRequest request) {
		if (request == null || request.nombre() == null || request.nombre().isBlank()
				|| request.nombre().trim().length() > 150 || request.tipo() == null || request.tipo().isBlank()
				|| request.tipo().trim().length() > 100) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Nombre y tipo son obligatorios (máximo 150 y 100 caracteres)");
		}
	}

	private static OngResponse toResponse(Ong ong) {
		return new OngResponse(ong.getId(), ong.getNombre(), ong.getTipo());
	}

	public record OngRequest(String nombre, String tipo) {
	}

	public record OngResponse(Long id, String nombre, String tipo) {
	}
}
