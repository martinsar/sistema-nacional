package ar.edu.unlp.sistemanacional.sistema_nacional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.seed.enabled=true"
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

    @Autowired
    private OngRepository ongRepository;

    @Autowired
    private RecursoRepository recursoRepository;

    @Autowired
    private SemillaDatosRunner semillaDatosRunner;

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

    // ---------- Compromiso de toda la convocatoria (transaccional) ----------

    private static DescontarRecursosService.ItemCompromiso item(String codigoOng, String tipo, int cantidad) {
        return new DescontarRecursosService.ItemCompromiso(tipo, codigoOng, cantidad);
    }

    @Test
    void commitsEveryChosenResourceOfTheConvocatoriaInOneOperation() {
        long convocatoria = SIGUIENTE_CONVOCATORIA.incrementAndGet();
        var ongA = ongService.create(new OngService.OngRequest("ONG " + UUID.randomUUID(), List.of("Alimentos")));
        var ongB = ongService.create(new OngService.OngRequest("ONG " + UUID.randomUUID(), List.of("Medicamentos")));
        var recursoA = recursoService.create(new RecursoService.RecursoRequest("Alimentos", 10, 0, ongA.id()));
        var recursoB = recursoService.create(new RecursoService.RecursoRequest("Medicamentos", 8, 0, ongB.id()));

        var resultado = descontarRecursosService.comprometerConvocatoria(convocatoria, List.of(
                item(ongA.codigoOng(), "ALIMENTOS", 4), item(ongB.codigoOng(), "MEDICAMENTOS", 8),
                item(ongA.codigoOng(), "alimentos", 2)));

        assertEquals(14, resultado.cantidadComprometida());
        assertEquals(2, resultado.compromisos().size());
        assertEquals(6, recursoService.findById(recursoA.id()).cantidadBloqueada());
        assertEquals(8, recursoService.findById(recursoB.id()).cantidadBloqueada());
        assertEquals(2, adjudicacionRepository.findByConvocatoriaIdOrderByIdAsc(convocatoria).size());
    }

    @Test
    void blocksNothingWhenOneOfTheChosenResourcesCannotBeCommitted() {
        long convocatoria = SIGUIENTE_CONVOCATORIA.incrementAndGet();
        var ongA = ongService.create(new OngService.OngRequest("ONG " + UUID.randomUUID(), List.of("Alimentos")));
        var ongB = ongService.create(new OngService.OngRequest("ONG " + UUID.randomUUID(), List.of("Medicamentos")));
        var recursoA = recursoService.create(new RecursoService.RecursoRequest("Alimentos", 10, 0, ongA.id()));
        var recursoB = recursoService.create(new RecursoService.RecursoRequest("Medicamentos", 3, 0, ongB.id()));

        var error = assertThrows(ResponseStatusException.class,
                () -> descontarRecursosService.comprometerConvocatoria(convocatoria, List.of(
                        item(ongA.codigoOng(), "ALIMENTOS", 5), item(ongB.codigoOng(), "MEDICAMENTOS", 4))));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        assertTrue(error.getReason().contains("No se comprometió ningún recurso de la convocatoria"));
        assertEquals(0, recursoService.findById(recursoA.id()).cantidadBloqueada());
        assertEquals(0, recursoService.findById(recursoB.id()).cantidadBloqueada());
        assertEquals(0, adjudicacionRepository.findByConvocatoriaIdOrderByIdAsc(convocatoria).size());
    }

    @Test
    void rejectsCommittingTheSameConvocatoriaTwiceAndEmptyRequests() {
        long convocatoria = SIGUIENTE_CONVOCATORIA.incrementAndGet();
        var ong = ongService.create(new OngService.OngRequest("ONG " + UUID.randomUUID(), List.of("Alimentos")));
        var recurso = recursoService.create(new RecursoService.RecursoRequest("Alimentos", 10, 0, ong.id()));
        descontarRecursosService.comprometerConvocatoria(convocatoria, List.of(item(ong.codigoOng(), "ALIMENTOS", 3)));

        var repetido = assertThrows(ResponseStatusException.class,
                () -> descontarRecursosService.comprometerConvocatoria(convocatoria,
                        List.of(item(ong.codigoOng(), "ALIMENTOS", 3))));
        assertEquals(HttpStatus.CONFLICT, repetido.getStatusCode());
        assertEquals(3, recursoService.findById(recurso.id()).cantidadBloqueada());

        var vacio = assertThrows(ResponseStatusException.class,
                () -> descontarRecursosService.comprometerConvocatoria(SIGUIENTE_CONVOCATORIA.incrementAndGet(), List.of()));
        assertEquals(HttpStatus.BAD_REQUEST, vacio.getStatusCode());
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

    @Test
    void usesStableOngCodeForIntegrationAcrossOffersResourcesAndCommitments() {
        String ongNombre = "ONG " + UUID.randomUUID();
        var ong = ongService.create(new OngService.OngRequest(ongNombre, List.of("Alimentos")));
        assertTrue(ong.codigoOng().startsWith("ONG-"));
        var otraOng = ongService.create(
                new OngService.OngRequest("Otra ONG " + UUID.randomUUID(), List.of("Alimentos")));
        assertNotEquals(ong.codigoOng(), otraOng.codigoOng());

        var recurso = recursoService.create(
                new RecursoService.RecursoRequest("Alimentos", 10, 0, ong.codigoOng(), null));
        assertEquals(ong.codigoOng(), recurso.codigoOng());

        Oferta ofertaPorCodigo = new Oferta("Nombre anterior", "Alimentos", 4, null);
        ofertaPorCodigo.setCodigo_ong(ong.codigoOng());
        var resultado = validarRecursosService.validarOfertas(List.of(ofertaPorCodigo));
        assertTrue(resultado.get(0).getHabilitado_flag());

        var compromiso = descontarRecursosService.descontar(
                new DescontarRecursosService.SolicitudDescuento(
                        "Alimentos", ong.codigoOng(), null, 4, SIGUIENTE_CONVOCATORIA.incrementAndGet()));
        assertEquals(4, compromiso.cantidadComprometida());
        assertEquals(ong.codigoOng(), compromiso.recursosCoincidentes().get(0).codigoOng());

        var ongActualizada = ongService.update(ong.id(),
                new OngService.OngRequest("Nombre actualizado " + UUID.randomUUID(), List.of("Alimentos")));
        assertEquals(ong.codigoOng(), ongActualizada.codigoOng());
    }

    @Test
    void acceptsAnExistingSharedCodeAndPreventsChangingOrReusingIt() {
        String codigoCompartido = "APP-ONG-" + UUID.randomUUID();
        var ong = ongService.create(
                new OngService.OngRequest("ONG con código compartido", List.of("Alimentos"), codigoCompartido));
        assertEquals(codigoCompartido, ong.codigoOng());
        assertEquals(ong.id(), ongService.findByCodigoOng(codigoCompartido).id());
        var actualizadaPorCodigo = ongService.updateByCodigoOng(codigoCompartido,
                new OngService.OngRequest("ONG actualizada por código", List.of("Alimentos")));
        assertEquals(ong.id(), actualizadaPorCodigo.id());
        assertEquals(codigoCompartido, actualizadaPorCodigo.codigoOng());

        var duplicada = assertThrows(ResponseStatusException.class,
                () -> ongService.create(new OngService.OngRequest(
                        "Otra ONG", List.of("Alimentos"), codigoCompartido)));
        assertEquals(HttpStatus.CONFLICT, duplicada.getStatusCode());

        var cambioCodigo = assertThrows(ResponseStatusException.class,
                () -> ongService.update(ong.id(), new OngService.OngRequest(
                        "ONG renombrada", List.of("Alimentos"), "OTRO-CODIGO")));
        assertEquals(HttpStatus.BAD_REQUEST, cambioCodigo.getStatusCode());
        assertEquals(codigoCompartido, ongService.findById(ong.id()).codigoOng());
    }


    // ---------- Catálogo de tipos de recurso ----------

    @Test
    void normalizesCodesNamesAndAliasesIgnoringCaseAccentsAndSpaces() {
        assertEquals("ALIMENTOS", TipoRecurso.normalizar("Alimentos").orElseThrow());
        assertEquals("ALIMENTOS", TipoRecurso.normalizar("  raciones   de ALIMENTO ").orElseThrow());
        assertEquals("PARAMEDICOS", TipoRecurso.normalizar("paramedicos").orElseThrow());
        assertEquals("PARAMEDICOS", TipoRecurso.normalizar("Paramédicos").orElseThrow());
        assertEquals("ROPA_ABRIGO", TipoRecurso.normalizar("ropa de abrigo").orElseThrow());
        assertEquals("ROPA_ABRIGO", TipoRecurso.normalizar("ROPA_ABRIGO").orElseThrow());
        assertEquals("GENERADORES", TipoRecurso.normalizar("Generadores eléctricos").orElseThrow());
        assertTrue(TipoRecurso.normalizar("Maquinaria").isEmpty());
        assertTrue(TipoRecurso.normalizar("  ").isEmpty());
        assertTrue(TipoRecurso.normalizar(null).isEmpty());
        assertEquals(11, TipoRecurso.values().length);
    }

    @Test
    void storesCanonicalCodesAndRejectsUnknownTypesWith400() {
        var ong = ongService.create(new OngService.OngRequest("ONG " + UUID.randomUUID(),
                List.of("Alimentos", "agua potable", "ALIMENTOS")));
        assertEquals(List.of("ALIMENTOS", "AGUA"), ong.tiposRecursos());

        var recurso = recursoService.create(new RecursoService.RecursoRequest("Paramédicos", 5, 0, ong.id()));
        assertEquals("PARAMEDICOS", recurso.tipo());

        var errorOng = assertThrows(ResponseStatusException.class, () -> ongService.create(
                new OngService.OngRequest("ONG " + UUID.randomUUID(), List.of("ALIMENTOS", "Maquinaria"))));
        assertEquals(HttpStatus.BAD_REQUEST, errorOng.getStatusCode());
        assertEquals("Tipo de recurso desconocido: Maquinaria", errorOng.getReason());

        var errorActualizar = assertThrows(ResponseStatusException.class, () -> ongService.update(ong.id(),
                new OngService.OngRequest("ONG", List.of("Inexistente"))));
        assertEquals(HttpStatus.BAD_REQUEST, errorActualizar.getStatusCode());

        var errorRecurso = assertThrows(ResponseStatusException.class,
                () -> recursoService.create(new RecursoService.RecursoRequest("Maquinaria", 5, 0, ong.id())));
        assertEquals(HttpStatus.BAD_REQUEST, errorRecurso.getStatusCode());
        assertEquals("Tipo de recurso desconocido: Maquinaria", errorRecurso.getReason());

        var errorActualizarRecurso = assertThrows(ResponseStatusException.class, () -> recursoService.update(
                recurso.id(), new RecursoService.RecursoRequest("Maquinaria", 5, 0, ong.id())));
        assertEquals(HttpStatus.BAD_REQUEST, errorActualizarRecurso.getStatusCode());
    }

    @Test
    void validatesBinaryByCodeAgainstOngSpecialization() {
        String ongNombre = "ONG " + UUID.randomUUID();
        ongService.create(new OngService.OngRequest(ongNombre, List.of("AGUA", "CARPAS")));

        var resultado = validarRecursosService.validarOfertas(List.of(
                new Oferta(ongNombre, "AGUA", 1, null),
                new Oferta(ongNombre, "agua potable", 1, null),
                new Oferta(ongNombre, "Carpas", 1, null),
                new Oferta(ongNombre, "BOMBEROS", 1, null),
                new Oferta(ongNombre, "Desconocido", 1, null),
                new Oferta(ongNombre, null, 1, null)));

        assertEquals(List.of(true, true, true, false, false, false),
                resultado.stream().map(Oferta::getHabilitado_flag).toList());
    }

    @Test
    void stillWorksWithLegacyDataStoredByName() {
        String codigo = "LEGADO-" + UUID.randomUUID();
        Ong ong = ongRepository.saveAndFlush(new Ong(codigo, "ONG vieja " + UUID.randomUUID(),
                new ArrayList<>(List.of("Alimentos", " Medicamentos "))));
        Recurso recurso = recursoRepository.saveAndFlush(new Recurso("Alimentos", 10, 0, ong));

        Oferta oferta = new Oferta("x", "ALIMENTOS", 1, null);
        oferta.setCodigo_ong(codigo);
        Oferta otra = new Oferta("x", "medicamentos", 1, null);
        otra.setCodigo_ong(codigo);
        Oferta noHabilitada = new Oferta("x", "AGUA", 1, null);
        noHabilitada.setCodigo_ong(codigo);
        assertEquals(List.of(true, true, false), validarRecursosService.validarOfertas(List.of(oferta, otra, noHabilitada))
                .stream().map(Oferta::getHabilitado_flag).toList());

        long convocatoria = SIGUIENTE_CONVOCATORIA.incrementAndGet();
        var compromiso = descontarRecursosService.descontar(new DescontarRecursosService.SolicitudDescuento(
                "ALIMENTOS", codigo, null, 4, convocatoria));
        assertEquals(4, compromiso.cantidadComprometida());
        assertEquals(4, recursoService.findById(recurso.getId()).cantidadBloqueada());

        liberarRecursosService.liberar(new LiberarRecursosService.SolicitudLiberacion(convocatoria));
        assertEquals(0, recursoService.findById(recurso.getId()).cantidadBloqueada());
    }

    @Test
    void seedIsIdempotentAndLoadsStockPerSpecialization() {
        semillaDatosRunner.sembrar();
        assertEquals(0, semillaDatosRunner.sembrar());

        var caritas = ongService.findByCodigoOng("ONG-CARITAS");
        assertEquals(List.of("ALIMENTOS", "AGUA", "FRAZADAS", "ROPA_ABRIGO", "CARPAS"), caritas.tiposRecursos());
        var recursos = recursoService.findAll().stream().filter(r -> "ONG-CARITAS".equals(r.codigoOng())).toList();
        assertEquals(5, recursos.size());
        assertTrue(recursos.stream().allMatch(r -> r.cantidadTotal() == 1000 && r.cantidadBloqueada() == 0));
        assertEquals(5, ongRepository.findAll().stream().filter(o -> o.getCodigoOng().matches(
                "ONG-(CARITAS|CRUZ-ROJA|BOMBEROS-LP|MEDICOS-MUNDO|DEFENSA-CIVIL)")).count());
    }

    // ---------- ABM ----------

    @Test
    void createsReadsUpdatesAndDeletesOngsAndResources() {
        String suffix = UUID.randomUUID().toString();
        var ong = ongService.create(new OngService.OngRequest("ONG " + suffix, List.of("AGUA")));

        var recurso = recursoService.create(new RecursoService.RecursoRequest("Alimentos", 20, 3, ong.id()));
        assertEquals(ong.id(), recurso.ongId());
        assertEquals(recurso.id(), recursoService.findById(recurso.id()).id());
        assertEquals(recurso.id(), recursoService.findAll().stream()
                .filter(item -> item.id().equals(recurso.id())).findFirst().orElseThrow().id());

        var updatedRecurso = recursoService.update(recurso.id(),
                new RecursoService.RecursoRequest("Medicamentos", 25, 5, ong.id()));
        assertEquals("MEDICAMENTOS", updatedRecurso.tipo());
        assertEquals(25, updatedRecurso.cantidadTotal());

        assertEquals(HttpStatus.CONFLICT,
                assertThrows(ResponseStatusException.class, () -> ongService.delete(ong.id())).getStatusCode());

        var updatedOng = ongService.update(ong.id(), new OngService.OngRequest("ONG actualizada " + suffix, List.of("CARPAS")));
        assertEquals(List.of("CARPAS"), updatedOng.tiposRecursos());
        assertEquals(updatedOng.id(), ongService.findById(ong.id()).id());

        recursoService.delete(recurso.id());
        ongService.deleteByCodigoOng(ong.codigoOng());
        assertEquals(HttpStatus.NOT_FOUND,
                assertThrows(ResponseStatusException.class, () -> ongService.findById(ong.id())).getStatusCode());
    }

    @Test
    void rejectsInvalidResourceQuantities() {
        var ong = ongService.create(new OngService.OngRequest("ONG " + UUID.randomUUID(), List.of("AGUA")));
        var error = assertThrows(ResponseStatusException.class,
                () -> recursoService.create(new RecursoService.RecursoRequest("Alimentos", 2, 3, ong.id())));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        ongService.delete(ong.id());
    }

    @Test
    void preventsDeletingResourcesWithReleasedAdjudications() {
        long convocatoria = SIGUIENTE_CONVOCATORIA.incrementAndGet();
        String ongNombre = "ONG " + UUID.randomUUID();
        var ong = ongService.create(new OngService.OngRequest(ongNombre, List.of("Alimentos")));
        var recurso = recursoService.create(new RecursoService.RecursoRequest("Alimentos", 5, 0, ong.id()));
        descontarRecursosService.descontar(solicitud(ongNombre, 2, convocatoria));
        liberarRecursosService.liberar(new LiberarRecursosService.SolicitudLiberacion(convocatoria));

        var error = assertThrows(ResponseStatusException.class, () -> recursoService.delete(recurso.id()));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        assertEquals(recurso.id(), recursoService.findById(recurso.id()).id());
    }
}
