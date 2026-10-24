package ar.edu.unlp.sistemanacional.sistema_nacional;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class DescontarRecursosService {

    private final RecursoRepository recursoRepository;

    public DescontarRecursosService(RecursoRepository recursoRepository) {
        this.recursoRepository = recursoRepository;
    }

    public DescuentoResponse descontar(SolicitudDescuento solicitud) {
        validar(solicitud);

        List<Recurso> recursos = recursoRepository
                .findByTipoIgnoreCaseAndOng_NombreIgnoreCaseOrderByIdAsc(
                        solicitud.tipoRecurso().trim(), solicitud.nombreOng().trim());
        List<RecursoDescontado> recursosCoincidentes = new ArrayList<>();
        int cantidadRestante = solicitud.cantidad();

        for (Recurso recurso : recursos) {
            int cantidadDisponible = recurso.getCantidadTotal() - recurso.getCantidadBloqueada();
            int cantidadDescontada = Math.min(cantidadRestante, cantidadDisponible);
            recurso.setCantidadBloqueada(recurso.getCantidadBloqueada() + cantidadDescontada);
            cantidadRestante -= cantidadDescontada;

            recursosCoincidentes.add(new RecursoDescontado(
                    recurso.getId(),
                    recurso.getTipo(),
                    recurso.getCantidadTotal(),
                    recurso.getCantidadBloqueada(),
                    recurso.getCantidadTotal() - recurso.getCantidadBloqueada(),
                    recurso.getOng().getId()));
        }

        return new DescuentoResponse(cantidadRestante, recursosCoincidentes);
    }

    private static void validar(SolicitudDescuento solicitud) {
        if (solicitud == null || solicitud.tipoRecurso() == null || solicitud.tipoRecurso().isBlank()
                || solicitud.nombreOng() == null || solicitud.nombreOng().isBlank()
                || solicitud.cantidad() == null || solicitud.cantidad() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El tipo de recurso, el nombre de la ONG y una cantidad no negativa son obligatorios");
        }
    }

    public record SolicitudDescuento(
            @JsonProperty("tipo_recurso") String tipoRecurso,
            @JsonProperty("nombre_ong") String nombreOng,
            Integer cantidad) {
    }

    public record DescuentoResponse(int cantidadRestante, List<RecursoDescontado> recursosCoincidentes) {
    }

    public record RecursoDescontado(
            Long id,
            String tipo,
            Integer cantidadTotal,
            Integer cantidadBloqueada,
            Integer cantidadDisponible,
            Long ongId) {
    }
}
