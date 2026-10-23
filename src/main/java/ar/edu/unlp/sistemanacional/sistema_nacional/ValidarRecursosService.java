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

            if (oferta.getOrganizacion_nombre() != null) {
                Optional<Ong> ongO = ongRepository.findByNombre(oferta.getOrganizacion_nombre().trim());

                if (ongO.isPresent()) {
                    Ong ong = ongO.get();
                    
                    List<String> tiposDisponibles = ong.getTipo();
                    
                    if (tiposDisponibles != null && oferta.getRecurso_tipo() != null) {
                        habilitado = tiposDisponibles.stream()
                            .anyMatch(tipo -> tipo.trim().equalsIgnoreCase(oferta.getRecurso_tipo().trim()));
                    }
                }
            }
            oferta.setHabilitado_flag(habilitado);

            return oferta;
        }).toList();
    }
}