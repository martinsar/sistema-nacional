package ar.edu.unlp.sistemanacional.sistema_nacional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.UUID;

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

    @Test
    void deductsRequestedQuantityFromMatchingDatabaseResourcesInOrder() {
        String ongNombre = "ONG " + UUID.randomUUID();
        var ong = ongService.create(new OngService.OngRequest(ongNombre, List.of("Alimentos")));
        var primera = recursoService.create(new RecursoService.RecursoRequest("Alimentos", 5, 0, ong.id()));
        var segunda = recursoService.create(new RecursoService.RecursoRequest("Alimentos", 5, 0, ong.id()));
        recursoService.create(new RecursoService.RecursoRequest("Medicamentos", 8, 0, ong.id()));
        ongService.create(new OngService.OngRequest("Otra ONG " + UUID.randomUUID(), List.of("Alimentos")));

        var resultado = descontarRecursosService.descontar(
                new DescontarRecursosService.SolicitudDescuento("Alimentos", ongNombre, 6));

        assertEquals(0, resultado.cantidadRestante());
        assertEquals(List.of(5, 5), resultado.recursosCoincidentes().stream()
                .map(DescontarRecursosService.RecursoDescontado::cantidadTotal).toList());
        assertEquals(List.of(5, 1), resultado.recursosCoincidentes().stream()
                .map(DescontarRecursosService.RecursoDescontado::cantidadBloqueada).toList());
        assertEquals(List.of(0, 4), resultado.recursosCoincidentes().stream()
                .map(DescontarRecursosService.RecursoDescontado::cantidadDisponible).toList());
        assertEquals(5, recursoService.findById(primera.id()).cantidadTotal());
        assertEquals(5, recursoService.findById(segunda.id()).cantidadTotal());
        assertEquals(5, recursoService.findById(primera.id()).cantidadBloqueada());
        assertEquals(1, recursoService.findById(segunda.id()).cantidadBloqueada());
    }

    @Test
    void onlyBlocksAvailableResourcesAndReturnsUnfulfilledAmount() {
        String ongNombre = "ONG " + UUID.randomUUID();
        var ong = ongService.create(new OngService.OngRequest(ongNombre, List.of("Alimentos")));
        recursoService.create(new RecursoService.RecursoRequest("Alimentos", 5, 3, ong.id()));

        var resultado = descontarRecursosService.descontar(
                new DescontarRecursosService.SolicitudDescuento("Alimentos", ongNombre, 4));

        assertEquals(2, resultado.cantidadRestante());
        assertEquals(5, resultado.recursosCoincidentes().get(0).cantidadTotal());
        assertEquals(5, resultado.recursosCoincidentes().get(0).cantidadBloqueada());
        assertEquals(0, resultado.recursosCoincidentes().get(0).cantidadDisponible());
    }

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