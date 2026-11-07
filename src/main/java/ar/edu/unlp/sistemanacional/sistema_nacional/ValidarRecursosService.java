package ar.edu.unlp.sistemanacional.sistema_nacional;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ValidarRecursosService {

    private final OngRepository ongRepository;

    public ValidarRecursosService(OngRepository ongRepository) {
        this.ongRepository = ongRepository;
    }

    public List<Oferta> validarOfertas(List<Oferta> ofertasRecibidas) {
        return ofertasRecibidas.stream().map(oferta -> {
            boolean habilitado = false;
            Optional<Ong> ongO = buscarOng(oferta);

            if (ongO.isPresent() && oferta.getRecurso_tipo() != null) {
                List<String> tiposDisponibles = ongO.get().getTipo();
                habilitado = tiposDisponibles != null && tiposDisponibles.stream()
                        .anyMatch(tipo -> tipo.trim().equalsIgnoreCase(oferta.getRecurso_tipo().trim()));
            }

            oferta.setHabilitado_flag(habilitado);
            return oferta;
        }).toList();
    }

    private Optional<Ong> buscarOng(Oferta oferta) {
        if (oferta.getCodigo_ong() != null && !oferta.getCodigo_ong().isBlank()) {
            return ongRepository.findByCodigoOng(oferta.getCodigo_ong().trim());
        }
        if (oferta.getOrganizacion_nombre() != null && !oferta.getOrganizacion_nombre().isBlank()) {
            return ongRepository.findByNombre(oferta.getOrganizacion_nombre().trim());
        }
        return Optional.empty();
    }
}