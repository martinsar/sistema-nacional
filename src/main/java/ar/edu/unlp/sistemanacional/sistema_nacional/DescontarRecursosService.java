package ar.edu.unlp.sistemanacional.sistema_nacional;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class DescontarRecursosService {

    private final RecursoRepository recursoRepository;
    private final AdjudicacionRepository adjudicacionRepository;

    public DescontarRecursosService(RecursoRepository recursoRepository,
            AdjudicacionRepository adjudicacionRepository) {
        this.recursoRepository = recursoRepository;
        this.adjudicacionRepository = adjudicacionRepository;
    }

    // Se compromete la cantidad completa o no se compromete nada
    public DescuentoResponse descontar(SolicitudDescuento solicitud) {
        validar(solicitud);
        String tipo = solicitud.tipoRecurso().trim();
        String codigoOng = solicitud.codigoOng() == null ? null : solicitud.codigoOng().trim();
        String nombreOng = solicitud.nombreOng() == null ? null : solicitud.nombreOng().trim();

        // El código compartido identifica a la ONG entre sistemas; el nombre queda por compatibilidad.
        List<Recurso> recursos = codigoOng != null && !codigoOng.isBlank()
                ? recursoRepository.findByTipoIgnoreCaseAndOng_CodigoOngOrderByIdAsc(tipo, codigoOng)
                : recursoRepository.findByTipoIgnoreCaseAndOng_NombreIgnoreCaseOrderByIdAsc(tipo, nombreOng);
        String identificadorOng = codigoOng != null && !codigoOng.isBlank() ? codigoOng : nombreOng;
        if (recursos.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "No se encontraron recursos del tipo '" + tipo + "' para la ONG '" + identificadorOng + "'");
        }

        // Se verifica la disponibilidad total antes de modificar cualquier recurso
        int disponibleTotal = recursos.stream()
                .mapToInt(r -> r.getCantidadTotal() - r.getCantidadBloqueada()).sum();
        if (disponibleTotal < solicitud.cantidad()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No hay disponibilidad suficiente de '" + tipo + "' en la ONG '" + identificadorOng
                            + "': se pidieron " + solicitud.cantidad() + " y hay " + disponibleTotal
                            + " disponibles. No se comprometió ninguna unidad");
        }

        List<RecursoDescontado> recursosCoincidentes = new ArrayList<>();
        int cantidadRestante = solicitud.cantidad();
        for (Recurso recurso : recursos) {
            if (cantidadRestante == 0) {
                break;
            }
            int cantidadDisponible = recurso.getCantidadTotal() - recurso.getCantidadBloqueada();
            int cantidadDescontada = Math.min(cantidadRestante, cantidadDisponible);
            if (cantidadDescontada == 0) {
                continue;
            }
            recurso.setCantidadBloqueada(recurso.getCantidadBloqueada() + cantidadDescontada);
            cantidadRestante -= cantidadDescontada;

            // Se registra la adjudicación para poder liberarla después por convocatoria
            adjudicacionRepository.save(new Adjudicacion(solicitud.idConvocatoria(), recurso.getOng(),
                    recurso, cantidadDescontada));

            recursosCoincidentes.add(new RecursoDescontado(
                    recurso.getId(),
                    recurso.getTipo(),
                    recurso.getCantidadTotal(),
                    recurso.getCantidadBloqueada(),
                    recurso.getCantidadTotal() - recurso.getCantidadBloqueada(),
                    recurso.getOng().getId(),
                    recurso.getOng().getCodigoOng()));
        }

        return new DescuentoResponse(solicitud.idConvocatoria(), solicitud.cantidad(),
                "Recursos comprometidos exitosamente", recursosCoincidentes);
    }

    private static void validar(SolicitudDescuento solicitud) {
        if (solicitud == null || solicitud.tipoRecurso() == null || solicitud.tipoRecurso().isBlank()
                || ((solicitud.codigoOng() == null || solicitud.codigoOng().isBlank())
                        && (solicitud.nombreOng() == null || solicitud.nombreOng().isBlank()))
                || solicitud.cantidad() == null || solicitud.cantidad() <= 0
                || solicitud.idConvocatoria() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El tipo de recurso, el código de ONG (o nombre en la ruta compatible), el id de la convocatoria y una cantidad mayor a cero son obligatorios");
        }
    }

    public record SolicitudDescuento(
            @JsonProperty("tipo_recurso") String tipoRecurso,
            @JsonProperty("codigo_ong") String codigoOng,
            @JsonProperty("nombre_ong")
            @Schema(description = "Campo legado; enviar codigo_ong para identificar la ONG entre sistemas.", deprecated = true)
            String nombreOng,
            Integer cantidad,
            @JsonProperty("id_convocatoria") Long idConvocatoria) {

        public SolicitudDescuento(String tipoRecurso, String nombreOng, Integer cantidad, Long idConvocatoria) {
            this(tipoRecurso, null, nombreOng, cantidad, idConvocatoria);
        }
    }

    public record DescuentoResponse(
            Long idConvocatoria,
            int cantidadComprometida,
            String mensaje,
            List<RecursoDescontado> recursosCoincidentes) {
    }

    public record RecursoDescontado(
            Long id,
            String tipo,
            Integer cantidadTotal,
            Integer cantidadBloqueada,
            Integer cantidadDisponible,
            Long ongId,
            @JsonProperty("codigo_ong") String codigoOng) {
    }
}
