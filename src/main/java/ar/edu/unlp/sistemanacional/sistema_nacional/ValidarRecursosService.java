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

            // [Entrega final] Se compara por código canónico, también con datos viejos guardados por nombre
            Optional<String> tipoOferta = TipoRecurso.normalizar(oferta.getRecurso_tipo());
            if (ongO.isPresent() && tipoOferta.isPresent()) {
                List<String> tiposDisponibles = ongO.get().getTipo();
                habilitado = tiposDisponibles != null && tiposDisponibles.stream()
                        .map(TipoRecurso::normalizar)
                        .anyMatch(tipo -> tipo.equals(tipoOferta));
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