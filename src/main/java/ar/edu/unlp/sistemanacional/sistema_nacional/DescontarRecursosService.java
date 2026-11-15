package ar.edu.unlp.sistemanacional.sistema_nacional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

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
        // [Entrega final] El tipo pedido se normaliza contra el catálogo
        String tipo = TipoRecurso.exigir(solicitud.tipoRecurso());
        String codigoOng = solicitud.codigoOng() == null ? null : solicitud.codigoOng().trim();
        String nombreOng = solicitud.nombreOng() == null ? null : solicitud.nombreOng().trim();

        // El código compartido identifica a la ONG entre sistemas; el nombre queda por compatibilidad.
        // [Entrega final] Se filtra por código canónico para tolerar recursos viejos guardados por nombre
        List<Recurso> recursosOng = codigoOng != null && !codigoOng.isBlank()
                ? recursoRepository.findByOng_CodigoOngOrderByIdAsc(codigoOng)
                : recursoRepository.findByOng_NombreIgnoreCaseOrderByIdAsc(nombreOng);
        List<Recurso> recursos = recursosOng.stream()
                .filter(r -> TipoRecurso.normalizar(r.getTipo()).filter(tipo::equals).isPresent())
                .toList();
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

    // [Entrega final] Se comprometen todos los recursos elegidos de una convocatoria en una sola transacción: todos o ninguno
    public CompromisoConvocatoriaResponse comprometerConvocatoria(Long idConvocatoria, List<ItemCompromiso> items) {
        if (idConvocatoria == null || items == null || items.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El id de la convocatoria y al menos un recurso a comprometer son obligatorios");
        }
        if (!adjudicacionRepository.findByConvocatoriaIdAndLiberadaFalseOrderByIdAsc(idConvocatoria).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La convocatoria " + idConvocatoria + " ya tiene recursos comprometidos sin liberar");
        }

        // Se agrupan los pedidos por ONG y tipo, ordenados para bloquear siempre en el mismo orden
        Map<String, ItemCompromiso> agrupados = new TreeMap<>();
        for (ItemCompromiso item : items) {
            if (item == null || item.codigoOng() == null || item.codigoOng().isBlank()
                    || item.tipoRecurso() == null || item.tipoRecurso().isBlank()
                    || item.cantidad() == null || item.cantidad() <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Cada recurso debe indicar codigo_ong, tipo_recurso y una cantidad mayor a cero");
            }
            String codigoOng = item.codigoOng().trim();
            String tipo = TipoRecurso.exigir(item.tipoRecurso());
            agrupados.merge(codigoOng + "|" + tipo, new ItemCompromiso(tipo, codigoOng, item.cantidad()),
                    (a, b) -> new ItemCompromiso(tipo, codigoOng, a.cantidad() + b.cantidad()));
        }

        List<DescuentoResponse> comprometidos = new ArrayList<>();
        int total = 0;
        try {
            for (ItemCompromiso item : agrupados.values()) {
                comprometidos.add(descontar(new SolicitudDescuento(item.tipoRecurso(), item.codigoOng(), null,
                        item.cantidad(), idConvocatoria)));
                total += item.cantidad();
            }
        } catch (ResponseStatusException e) {
            // La excepción revierte toda la transacción: no queda ningún recurso bloqueado
            throw new ResponseStatusException(e.getStatusCode(),
                    e.getReason() + ". No se comprometió ningún recurso de la convocatoria");
        }
        return new CompromisoConvocatoriaResponse(idConvocatoria, total,
                "Se comprometieron todos los recursos de la convocatoria", comprometidos);
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

    public record ItemCompromiso(
            @JsonProperty("tipo_recurso") String tipoRecurso,
            @JsonProperty("codigo_ong") String codigoOng,
            Integer cantidad) {
    }

    public record CompromisoConvocatoriaResponse(
            @JsonProperty("id_convocatoria") Long idConvocatoria,
            @JsonProperty("cantidad_comprometida") int cantidadComprometida,
            String mensaje,
            List<DescuentoResponse> compromisos) {
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
