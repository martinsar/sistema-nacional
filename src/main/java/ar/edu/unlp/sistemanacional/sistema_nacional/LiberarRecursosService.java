package ar.edu.unlp.sistemanacional.sistema_nacional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class LiberarRecursosService {

    private static final String MENSAJE_OK = "Recursos liberados exitosamente";
    private static final String MENSAJE_CON_OMITIDOS = MENSAJE_OK
            + ". Algunos de los recursos adjudicados ya no existen en la base de ciertas ONGs y no se pudieron liberar";

    private final AdjudicacionRepository adjudicacionRepository;
    private final RecursoRepository recursoRepository;

    public LiberarRecursosService(AdjudicacionRepository adjudicacionRepository,
            RecursoRepository recursoRepository) {
        this.adjudicacionRepository = adjudicacionRepository;
        this.recursoRepository = recursoRepository;
    }

    public LiberacionResponse liberar(SolicitudLiberacion solicitud) {
        if (solicitud == null || solicitud.idConvocatoria() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El id de la convocatoria es obligatorio");
        }

        // Se buscan las adjudicaciones de la convocatoria que todavía no se liberaron
        List<Adjudicacion> pendientes = adjudicacionRepository
                .findByConvocatoriaIdAndLiberadaFalseOrderByIdAsc(solicitud.idConvocatoria());
        if (pendientes.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "La convocatoria no tiene adjudicaciones pendientes de liberar");
        }

        // Se bloquean los recursos ordenados por id para evitar deadlocks entre liberaciones
        List<Long> idsRecursos = pendientes.stream().map(a -> a.getRecurso().getId()).distinct().toList();
        Map<Long, Recurso> recursos = recursoRepository.findAllByIdInOrderByIdAsc(idsRecursos).stream()
                .collect(Collectors.toMap(Recurso::getId, Function.identity()));

        List<RecursoLiberado> liberados = new ArrayList<>();
        int cantidadTotalLiberada = 0;
        boolean hayOmitidos = false;
        for (Adjudicacion adjudicacion : pendientes) {
            Recurso recurso = recursos.get(adjudicacion.getRecurso().getId());

            // Si el recurso ya no es de la ONG, no se toca su stock pero se sigue con los demás
            if (recurso == null || !recurso.getOng().getId().equals(adjudicacion.getOng().getId())) {
                adjudicacion.marcarLiberada();
                hayOmitidos = true;
                continue;
            }

            recurso.setCantidadBloqueada(Math.max(0, recurso.getCantidadBloqueada() - adjudicacion.getCantidad()));
            adjudicacion.marcarLiberada();
            cantidadTotalLiberada += adjudicacion.getCantidad();

            liberados.add(new RecursoLiberado(
                    recurso.getId(),
                    recurso.getTipo(),
                    adjudicacion.getCantidad(),
                    recurso.getCantidadBloqueada(),
                    recurso.getCantidadTotal() - recurso.getCantidadBloqueada(),
                    adjudicacion.getOng().getId()));
        }

        return new LiberacionResponse(solicitud.idConvocatoria(), hayOmitidos ? MENSAJE_CON_OMITIDOS : MENSAJE_OK,
                liberados.size(), cantidadTotalLiberada, liberados);
    }

    public record SolicitudLiberacion(@JsonProperty("id_convocatoria") Long idConvocatoria) {
    }

    public record LiberacionResponse(
            Long idConvocatoria,
            String mensaje,
            int adjudicacionesLiberadas,
            int cantidadTotalLiberada,
            List<RecursoLiberado> recursosLiberados) {
    }

    public record RecursoLiberado(
            Long recursoId,
            String tipo,
            Integer cantidadLiberada,
            Integer cantidadBloqueada,
            Integer cantidadDisponible,
            Long ongId) {
    }
}
