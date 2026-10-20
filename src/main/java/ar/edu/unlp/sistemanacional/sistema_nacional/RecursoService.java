package ar.edu.unlp.sistemanacional.sistema_nacional;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class RecursoService {

	private final RecursoRepository recursoRepository;
	private final OngRepository ongRepository;

	public RecursoService(RecursoRepository recursoRepository, OngRepository ongRepository) {
		this.recursoRepository = recursoRepository;
		this.ongRepository = ongRepository;
	}

	@Transactional(readOnly = true)
	public List<RecursoResponse> findAll() {
		return recursoRepository.findAll().stream().map(RecursoService::toResponse).toList();
	}

	@Transactional(readOnly = true)
	public RecursoResponse findById(Long id) {
		return toResponse(getRecurso(id));
	}

	public RecursoResponse create(RecursoRequest request) {
		validate(request);
		Ong ong = getOng(request.ongId());
		Recurso recurso = new Recurso(request.tipo().trim(), request.cantidadTotal(),
				request.cantidadBloqueada(), ong);
		return toResponse(recursoRepository.save(recurso));
	}

	public RecursoResponse update(Long id, RecursoRequest request) {
		validate(request);
		Recurso recurso = getRecurso(id);
		recurso.setTipo(request.tipo().trim());
		recurso.setCantidadTotal(request.cantidadTotal());
		recurso.setCantidadBloqueada(request.cantidadBloqueada());
		recurso.setOng(getOng(request.ongId()));
		return toResponse(recursoRepository.save(recurso));
	}

	public void delete(Long id) {
		recursoRepository.delete(getRecurso(id));
	}

	private Recurso getRecurso(Long id) {
		return recursoRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recurso no encontrado"));
	}

	private Ong getOng(Long id) {
		if (id == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El id de la ONG es obligatorio");
		}
		return ongRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ONG no encontrada"));
	}

	private static void validate(RecursoRequest request) {
		if (request == null || request.tipo() == null || request.tipo().isBlank()
				|| request.tipo().trim().length() > 100 || request.cantidadTotal() == null
				|| request.cantidadBloqueada() == null || request.cantidadTotal() < 0
				|| request.cantidadBloqueada() < 0 || request.cantidadBloqueada() > request.cantidadTotal()
				|| request.ongId() == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"El tipo, las cantidades y la ONG son obligatorios; las cantidades deben ser válidas");
		}
	}

	private static RecursoResponse toResponse(Recurso recurso) {
		return new RecursoResponse(recurso.getId(), recurso.getTipo(), recurso.getCantidadTotal(),
				recurso.getCantidadBloqueada(), recurso.getOng().getId());
	}

	public record RecursoRequest(String tipo, Integer cantidadTotal, Integer cantidadBloqueada, Long ongId) {
	}

	public record RecursoResponse(Long id, String tipo, Integer cantidadTotal, Integer cantidadBloqueada,
			Long ongId) {
	}
}
