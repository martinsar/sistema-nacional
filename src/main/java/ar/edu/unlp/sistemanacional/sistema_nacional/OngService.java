package ar.edu.unlp.sistemanacional.sistema_nacional;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.dao.DataIntegrityViolationException;
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

    @Transactional(readOnly = true)
    public OngResponse findByCodigoOng(String codigoOng) {
        return toResponse(getOngByCodigoOng(codigoOng));
    }

    public OngResponse create(OngRequest request) {
        validate(request);
        String codigoOng = request.codigoOng() == null ? null : request.codigoOng().trim();
        if (codigoOng != null && ongRepository.existsByCodigoOng(codigoOng)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe una ONG con ese codigo_ong");
        }
        Ong ong = new Ong(codigoOng, request.nombre().trim(), request.tiposRecursos());
        try {
            return toResponse(ongRepository.saveAndFlush(ong));
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe una ONG con ese codigo_ong", e);
        }
    }

    public OngResponse update(Long id, OngRequest request) {
        validate(request);
        return update(getOng(id), request);
    }

    public OngResponse updateByCodigoOng(String codigoOng, OngRequest request) {
        validate(request);
        return update(getOngByCodigoOng(codigoOng), request);
    }

    private OngResponse update(Ong ong, OngRequest request) {
        if (request.codigoOng() != null && !request.codigoOng().isBlank()
                && !request.codigoOng().trim().equals(ong.getCodigoOng())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El codigo_ong no puede modificarse");
        }
        ong.setNombre(request.nombre().trim());
        List<String> nuevosTipos = request.tiposRecursos() != null 
            ? new ArrayList<>(request.tiposRecursos()) 
            : new ArrayList<>();
        ong.setTipo(nuevosTipos);
        return toResponse(ongRepository.save(ong));
    }

    public void delete(Long id) {
        delete(getOng(id));
    }

    public void deleteByCodigoOng(String codigoOng) {
        delete(getOngByCodigoOng(codigoOng));
    }

    private void delete(Ong ong) {
        if (recursoRepository.existsByOngId(ong.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No se puede eliminar una ONG que tiene recursos");
        }
        ongRepository.delete(ong);
    }

    private Ong getOng(Long id) {
        return ongRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ONG no encontrada"));
    }

    private Ong getOngByCodigoOng(String codigoOng) {
        if (codigoOng == null || codigoOng.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El codigo_ong es obligatorio");
        }
        return ongRepository.findByCodigoOng(codigoOng.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ONG no encontrada"));
    }

    private static void validate(OngRequest request) {
        if (request == null || request.nombre() == null || request.nombre().isBlank()
                || request.nombre().trim().length() > 150 || request.tiposRecursos() == null
                || request.tiposRecursos().isEmpty()
                || (request.codigoOng() != null
                        && (request.codigoOng().isBlank() || request.codigoOng().trim().length() > 100))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Nombre, al menos un tipo de recurso y un codigo_ong válido si se especifica son obligatorios");
        }
    }

    private static OngResponse toResponse(Ong ong) {
        return new OngResponse(ong.getId(), ong.getCodigoOng(), ong.getNombre(), ong.getTipo());
    }

    public record OngRequest(String nombre, List<String> tiposRecursos,
            @JsonProperty("codigo_ong")
            @Schema(description = "Identificador estable compartido entre sistemas. Si se omite, el Sistema Nacional genera uno.")
            String codigoOng) {
        public OngRequest(String nombre, List<String> tiposRecursos) {
            this(nombre, tiposRecursos, null);
        }
    }

    public record OngResponse(Long id,
            @JsonProperty("codigo_ong")
            @Schema(description = "Identificador estable de la ONG para referencias entre sistemas.")
            String codigoOng,
            String nombre, List<String> tiposRecursos) {
    }
}