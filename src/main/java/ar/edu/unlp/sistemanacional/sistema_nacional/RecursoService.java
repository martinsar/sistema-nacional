package ar.edu.unlp.sistemanacional.sistema_nacional;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class RecursoService {

	private final RecursoRepository recursoRepository;
	private final OngRepository ongRepository;
	private final AdjudicacionRepository adjudicacionRepository;

	public RecursoService(RecursoRepository recursoRepository, OngRepository ongRepository,
			AdjudicacionRepository adjudicacionRepository) {
		this.recursoRepository = recursoRepository;
		this.ongRepository = ongRepository;
		this.adjudicacionRepository = adjudicacionRepository;
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
		Ong ong = getOng(request.codigoOng(), request.ongId());
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
		recurso.setOng(getOng(request.codigoOng(), request.ongId()));
		return toResponse(recursoRepository.save(recurso));
	}

	public void delete(Long id) {
		Recurso recurso = getRecurso(id);
		if (adjudicacionRepository.existsByRecursoId(id)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"No se puede eliminar un recurso con adjudicaciones registradas");
		}
		recursoRepository.delete(recurso);
	}

	private Recurso getRecurso(Long id) {
		return recursoRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recurso no encontrado"));
	}

	private Ong getOng(String codigoOng, Long id) {
		if (codigoOng != null && !codigoOng.isBlank()) {
			Ong ong = ongRepository.findByCodigoOng(codigoOng.trim())
					.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ONG no encontrada"));
			if (id != null && !ong.getId().equals(id)) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
						"El codigo_ong y el ongId indican ONGs distintas");
			}
			return ong;
		}
		if (id == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El codigo_ong de la ONG es obligatorio");
		}
		return ongRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ONG no encontrada"));
	}

	private static void validate(RecursoRequest request) {
		if (request == null || request.tipo() == null || request.tipo().isBlank()
				|| request.tipo().trim().length() > 100 || request.cantidadTotal() == null
				|| request.cantidadBloqueada() == null || request.cantidadTotal() < 0
				|| request.cantidadBloqueada() < 0 || request.cantidadBloqueada() > request.cantidadTotal()
				|| ((request.codigoOng() == null || request.codigoOng().isBlank()) && request.ongId() == null)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"El tipo, las cantidades y codigo_ong de la ONG son obligatorios; las cantidades deben ser válidas");
		}
	}

	private static RecursoResponse toResponse(Recurso recurso) {
		return new RecursoResponse(recurso.getId(), recurso.getTipo(), recurso.getCantidadTotal(),
				recurso.getCantidadBloqueada(), recurso.getOng().getId(), recurso.getOng().getCodigoOng());
	}

	public record RecursoRequest(String tipo, Integer cantidadTotal, Integer cantidadBloqueada,
			@JsonProperty("codigo_ong") String codigoOng,
			@Schema(description = "Campo legado; preferir codigo_ong para referenciar una ONG entre sistemas.", deprecated = true)
			Long ongId) {
		public RecursoRequest(String tipo, Integer cantidadTotal, Integer cantidadBloqueada, Long ongId) {
			this(tipo, cantidadTotal, cantidadBloqueada, null, ongId);
		}
	}

	public record RecursoResponse(Long id, String tipo, Integer cantidadTotal, Integer cantidadBloqueada,
			Long ongId, @JsonProperty("codigo_ong") String codigoOng) {
	}
}
