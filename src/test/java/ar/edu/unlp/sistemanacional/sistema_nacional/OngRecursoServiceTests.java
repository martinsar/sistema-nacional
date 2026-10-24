package ar.edu.unlp.sistemanacional.sistema_nacional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest(properties = {
        "app.security.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "spring.datasource.url=jdbc:h2:mem:ong-recurso;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class OngRecursoServiceTests {

    @Autowired
    private OngService ongService;

    @Autowired
    private RecursoService recursoService;

    @Autowired
    private DescontarRecursosService descontarRecursosService;

    @Autowired
    private LiberarRecursosService liberarRecursosService;

    @Autowired
    private ValidarRecursosService validarRecursosService;

    @Autowired
    private AdjudicacionRepository adjudicacionRepository;

    // Cada test usa su propia convocatoria para no mezclar adjudicaciones
    private static final AtomicLong SIGUIENTE_CONVOCATORIA = new AtomicLong(1000);

    private static DescontarRecursosService.SolicitudDescuento solicitud(String ongNombre, int cantidad,
            Long convocatoria) {
        return new DescontarRecursosService.SolicitudDescuento("Alimentos", ongNombre, cantidad, convocatoria);
    }

    // ---------- Compromiso ----------

    @Test
    void deductsRequestedQuantityFromMatchingDatabaseResourcesInOrder() {
        String ongNombre = "ONG " + UUID.randomUUID();
        var ong = ongService.create(new OngService.OngRequest(ongNombre, List.of("Alimentos")));
        var primera = recursoService.create(new RecursoService.RecursoRequest("Alimentos", 5, 0, ong.id()));
        var segunda = recursoService.create(new RecursoService.RecursoRequest("Alimentos", 5, 0, ong.id()));
        recursoService.create(new RecursoService.RecursoRequest("Medicamentos", 8, 0, ong.id()));
        ongService.create(new OngService.OngRequest("Otra ONG " + UUID.randomUUID(), List.of("Alimentos")));

        var resultado = descontarRecursosService.descontar(
                solicitud(ongNombre, 6, SIGUIENTE_CONVOCATORIA.incrementAndGet()));

        assertEquals(6, resultado.cantidadComprometida());
        assertEquals(List.of(5, 5), resultado.recursosCoincidentes().stream()
                .map(DescontarRecursosService.RecursoDescontado::cantidadTotal).toList());
        assertEquals(List.of(5, 1), resultado.recursosCoincidentes().stream()
                .map(DescontarRecursosService.RecursoDescontado::cantidadBloqueada).toList());
        assertEquals(List.of(0, 4), resultado.recursosCoincidentes().stream()
                .map(DescontarRecursosService.RecursoDescontado::cantidadDisponible).toList());
        assertEquals(5, recursoService.findById(primera.id()).cantidadBloqueada());
        assertEquals(1, recursoService.findById(segunda.id()).cantidadBloqueada());
    }

    @Test
    void rejectsCommitmentWhenThereIsNotEnoughAvailabilityAndBlocksNothing() {
        long convocatoria = SIGUIENTE_CONVOCATORIA.incrementAndGet();
        String ongNombre = "ONG " + UUID.randomUUID();
        var ong = ongService.create(new OngService.OngRequest(ongNombre, List.of("Alimentos")));
        var recurso = recursoService.create(new RecursoService.RecursoRequest("Alimentos", 5, 3, ong.id()));

        var error = assertThrows(ResponseStatusException.class,
                () -> descontarRecursosService.descontar(solicitud(ongNombre, 4, convocatoria)));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        assertTrue(error.getReason().contains("No se comprometió ninguna unidad"));
        assertEquals(3, recursoService.findById(recurso.id()).cantidadBloqueada());
        assertEquals(0, adjudicacionRepository.findByConvocatoriaIdOrderByIdAsc(convocatoria).size());
    }

    @Test
    void rejectsCommitmentThatOnlyFitsPartiallyAcrossSeveralResources() {
        long convocatoria = SIGUIENTE_CONVOCATORIA.incrementAndGet();
        String ongNombre = "ONG " + UUID.randomUUID();
        var ong = ongService.create(new OngService.OngRequest(ongNombre, List.of("Alimentos")));
        var primera = recursoService.create(new RecursoService.RecursoRequest("Alimentos", 5, 4, ong.id()));
        var segunda = recursoService.create(new RecursoService.RecursoRequest("Alimentos", 5, 0, ong.id()));

        // Hay 1 + 5 = 6 disponibles y se piden 7
        var error = assertThrows(ResponseStatusException.class,
                () -> descontarRecursosService.descontar(solicitud(ongNombre, 7, convocatoria)));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        assertEquals(4, recursoService.findById(primera.id()).cantidadBloqueada());
        assertEquals(0, recursoService.findById(segunda.id()).cantidadBloqueada());
        assertEquals(0, adjudicacionRepository.findByConvocatoriaIdOrderByIdAsc(convocatoria).size());
    }

    @Test
    void rejectsCommitmentWhenTheOngHasNoResourcesOfThatType() {
        String ongNombre = "ONG " + UUID.randomUUID();
        ongService.create(new OngService.OngRequest(ongNombre, List.of("Alimentos")));

        var error = assertThrows(ResponseStatusException.class,
                () -> descontarRecursosService.descontar(solicitud(ongNombre, 1, SIGUIENTE_CONVOCATORIA.incrementAndGet())));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
    }

    @Test
    void registersOneAdjudicacionPerBlockedResourceWithItsOng() {
        long convocatoria = SIGUIENTE_CONVOCATORIA.incrementAndGet();
        String ongNombre = "ONG " + UUID.randomUUID();
        var ong = ongService.create(new OngService.OngRequest(ongNombre, List.of("Alimentos")));
        recursoService.create(new RecursoService.RecursoRequest("Alimentos", 5, 0, ong.id()));
        recursoService.create(new RecursoService.RecursoRequest("Alimentos", 5, 0, ong.id()));

        descontarRecursosService.descontar(solicitud(ongNombre, 6, convocatoria));

        var adjudicaciones = adjudicacionRepository.findByConvocatoriaIdOrderByIdAsc(convocatoria);
        assertEquals(List.of(5, 1), adjudicaciones.stream().map(Adjudicacion::getCantidad).toList());
        assertEquals(List.of(false, false), adjudicaciones.stream().map(Adjudicacion::isLiberada).toList());
        assertEquals(List.of(ong.id(), ong.id()),
                adjudicaciones.stream().map(a -> a.getOng().getId()).toList());
    }

    @Test
    void rejectsCommitmentWithoutConvocatoriaOrWithNonPositiveQuantity() {
        var sinConvocatoria = assertThrows(ResponseStatusException.class,
                () -> descontarRecursosService.descontar(solicitud("Cualquiera", 3, null)));
        assertEquals(HttpStatus.BAD_REQUEST, sinConvocatoria.getStatusCode());

        var cantidadCero = assertThrows(ResponseStatusException.class,
                () -> descontarRecursosService.descontar(solicitud("Cualquiera", 0, 1L)));
        assertEquals(HttpStatus.BAD_REQUEST, cantidadCero.getStatusCode());
    }

    @Test
    void concurrentCommitmentsNeverBlockMoreThanTheTotal() throws Exception {
        String ongNombre = "ONG " + UUID.randomUUID();
        var ong = ongService.create(new OngService.OngRequest(ongNombre, List.of("Alimentos")));
        var recurso = recursoService.create(new RecursoService.RecursoRequest("Alimentos", 5, 0, ong.id()));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch inicio = new CountDownLatch(1);
        List<Future<DescontarRecursosService.DescuentoResponse>> futuros = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            long convocatoria = SIGUIENTE_CONVOCATORIA.incrementAndGet();
            futuros.add(pool.submit(() -> {
                inicio.await();
                return descontarRecursosService.descontar(solicitud(ongNombre, 4, convocatoria));
            }));
        }
        inicio.countDown();

        // Se piden 4 + 4 sobre 5 disponibles: uno se compromete completo y el otro se rechaza
        int comprometidos = 0;
        int rechazados = 0;
        for (var futuro : futuros) {
            try {
                futuro.get(15, TimeUnit.SECONDS);
                comprometidos++;
            } catch (ExecutionException e) {
                assertEquals(HttpStatus.CONFLICT, ((ResponseStatusException) e.getCause()).getStatusCode());
                rechazados++;
            }
        }
        pool.shutdown();

        assertEquals(1, comprometidos);
        assertEquals(1, rechazados);
        assertEquals(4, recursoService.findById(recurso.id()).cantidadBloqueada());
    }

    // ---------- Liberación ----------

    @Test
    void releasesEveryResourceBlockedForTheConvocatoria() {
        long convocatoria = SIGUIENTE_CONVOCATORIA.incrementAndGet();
        String ongNombre = "ONG " + UUID.randomUUID();
        var ong = ongService.create(new OngService.OngRequest(ongNombre, List.of("Alimentos")));
        var primera = recursoService.create(new RecursoService.RecursoRequest("Alimentos", 5, 0, ong.id()));
        var segunda = recursoService.create(new RecursoService.RecursoRequest("Alimentos", 5, 0, ong.id()));
        descontarRecursosService.descontar(solicitud(ongNombre, 6, convocatoria));

        var resultado = liberarRecursosService.liberar(new LiberarRecursosService.SolicitudLiberacion(convocatoria));

        assertEquals("Recursos liberados exitosamente", resultado.mensaje());
        assertEquals(2, resultado.adjudicacionesLiberadas());
        assertEquals(6, resultado.cantidadTotalLiberada());
        assertEquals(List.of(5, 1), resultado.recursosLiberados().stream()
                .map(LiberarRecursosService.RecursoLiberado::cantidadLiberada).toList());
        assertEquals(0, recursoService.findById(primera.id()).cantidadBloqueada());
        assertEquals(0, recursoService.findById(segunda.id()).cantidadBloqueada());
        assertEquals(List.of(true, true), adjudicacionRepository.findByConvocatoriaIdOrderByIdAsc(convocatoria)
                .stream().map(Adjudicacion::isLiberada).toList());
    }

    @Test
    void releasingOneConvocatoriaKeepsTheOthersBlocked() {
        long convocatoriaA = SIGUIENTE_CONVOCATORIA.incrementAndGet();
        long convocatoriaB = SIGUIENTE_CONVOCATORIA.incrementAndGet();
        String ongNombre = "ONG " + UUID.randomUUID();
        var ong = ongService.create(new OngService.OngRequest(ongNombre, List.of("Alimentos")));
        var recurso = recursoService.create(new RecursoService.RecursoRequest("Alimentos", 10, 0, ong.id()));
        descontarRecursosService.descontar(solicitud(ongNombre, 3, convocatoriaA));
        descontarRecursosService.descontar(solicitud(ongNombre, 4, convocatoriaB));

        liberarRecursosService.liberar(new LiberarRecursosService.SolicitudLiberacion(convocatoriaA));

        assertEquals(4, recursoService.findById(recurso.id()).cantidadBloqueada());
    }

    @Test
    void stillReleasesTheOtherResourcesWhenOneNoLongerBelongsToItsOng() {
        long convocatoria = SIGUIENTE_CONVOCATORIA.incrementAndGet();
        String ongNombre = "ONG " + UUID.randomUUID();
        var ong = ongService.create(new OngService.OngRequest(ongNombre, List.of("Alimentos")));
        var otraOng = ongService.create(new OngService.OngRequest("Otra ONG " + UUID.randomUUID(), List.of("Alimentos")));
        var primera = recursoService.create(new RecursoService.RecursoRequest("Alimentos", 5, 0, ong.id()));
        var segunda = recursoService.create(new RecursoService.RecursoRequest("Alimentos", 5, 0, ong.id()));
        descontarRecursosService.descontar(solicitud(ongNombre, 6, convocatoria));

        // El segundo recurso pasa a otra ONG antes de la liberación
        recursoService.update(segunda.id(), new RecursoService.RecursoRequest("Alimentos", 5, 1, otraOng.id()));

        var resultado = liberarRecursosService.liberar(new LiberarRecursosService.SolicitudLiberacion(convocatoria));

        assertTrue(resultado.mensaje().startsWith("Recursos liberados exitosamente"));
        assertTrue(resultado.mensaje().contains("ya no existen en la base de ciertas ONGs"));
        assertEquals(1, resultado.adjudicacionesLiberadas());
        assertEquals(5, resultado.cantidadTotalLiberada());
        assertEquals(0, recursoService.findById(primera.id()).cantidadBloqueada());
        assertEquals(1, recursoService.findById(segunda.id()).cantidadBloqueada());
        assertEquals(List.of(true, true), adjudicacionRepository.findByConvocatoriaIdOrderByIdAsc(convocatoria)
                .stream().map(Adjudicacion::isLiberada).toList());
    }

    @Test
    void cannotReleaseTheSameConvocatoriaTwice() {
        long convocatoria = SIGUIENTE_CONVOCATORIA.incrementAndGet();
        String ongNombre = "ONG " + UUID.randomUUID();
        var ong = ongService.create(new OngService.OngRequest(ongNombre, List.of("Alimentos")));
        recursoService.create(new RecursoService.RecursoRequest("Alimentos", 5, 0, ong.id()));
        descontarRecursosService.descontar(solicitud(ongNombre, 2, convocatoria));
        liberarRecursosService.liberar(new LiberarRecursosService.SolicitudLiberacion(convocatoria));

        var error = assertThrows(ResponseStatusException.class,
                () -> liberarRecursosService.liberar(new LiberarRecursosService.SolicitudLiberacion(convocatoria)));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
    }

    @Test
    void rejectsReleaseOfUnknownConvocatoriaOrWithoutId() {
        var inexistente = assertThrows(ResponseStatusException.class,
                () -> liberarRecursosService.liberar(new LiberarRecursosService.SolicitudLiberacion(999999L)));
        assertEquals(HttpStatus.NOT_FOUND, inexistente.getStatusCode());

        var sinId = assertThrows(ResponseStatusException.class,
                () -> liberarRecursosService.liberar(new LiberarRecursosService.SolicitudLiberacion(null)));
        assertEquals(HttpStatus.BAD_REQUEST, sinId.getStatusCode());
    }

    // ---------- Validación de ofertas ----------

    @Test
    void enablesOffersOnlyWhenTheTypeMatchesOneOfTheOngTypes() {
        String ongNombre = "ONG " + UUID.randomUUID();
        ongService.create(new OngService.OngRequest(ongNombre, List.of("Alimentos", "Medicamentos")));

        var resultado = validarRecursosService.validarOfertas(List.of(
                new Oferta(ongNombre, "alimentos", 10, null),
                new Oferta(ongNombre, "Medicamentos", 5, null),
                new Oferta(ongNombre, "Maquinaria", 1, null),
                new Oferta("ONG inexistente", "Alimentos", 1, null)));

        assertEquals(List.of(true, true, false, false),
                resultado.stream().map(Oferta::getHabilitado_flag).toList());
    }

    // ---------- ABM ----------

    @Test
    void createsReadsUpdatesAndDeletesOngsAndResources() {
        String suffix = UUID.randomUUID().toString();
        var ong = ongService.create(new OngService.OngRequest("ONG " + suffix, List.of("Asistencia")));

        var recurso = recursoService.create(new RecursoService.RecursoRequest("Alimentos", 20, 3, ong.id()));
        assertEquals(ong.id(), recurso.ongId());
        assertEquals(recurso.id(), recursoService.findById(recurso.id()).id());
        assertEquals(recurso.id(), recursoService.findAll().stream()
                .filter(item -> item.id().equals(recurso.id())).findFirst().orElseThrow().id());

        var updatedRecurso = recursoService.update(recurso.id(),
                new RecursoService.RecursoRequest("Medicamentos", 25, 5, ong.id()));
        assertEquals("Medicamentos", updatedRecurso.tipo());
        assertEquals(25, updatedRecurso.cantidadTotal());

        assertEquals(HttpStatus.CONFLICT,
                assertThrows(ResponseStatusException.class, () -> ongService.delete(ong.id())).getStatusCode());

        var updatedOng = ongService.update(ong.id(), new OngService.OngRequest("ONG actualizada " + suffix, List.of("Salud")));
        assertEquals(List.of("Salud"), updatedOng.tiposRecursos());
        assertEquals(updatedOng.id(), ongService.findById(ong.id()).id());

        recursoService.delete(recurso.id());
        ongService.delete(ong.id());
        assertEquals(HttpStatus.NOT_FOUND,
                assertThrows(ResponseStatusException.class, () -> ongService.findById(ong.id())).getStatusCode());
    }

    @Test
    void rejectsInvalidResourceQuantities() {
        var ong = ongService.create(new OngService.OngRequest("ONG " + UUID.randomUUID(), List.of("Asistencia")));
        var error = assertThrows(ResponseStatusException.class,
                () -> recursoService.create(new RecursoService.RecursoRequest("Alimentos", 2, 3, ong.id())));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        ongService.delete(ong.id());
    }
}
