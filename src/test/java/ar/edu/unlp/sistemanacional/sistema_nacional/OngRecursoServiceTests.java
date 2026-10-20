package ar.edu.unlp.sistemanacional.sistema_nacional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

	@Test
	void createsReadsUpdatesAndDeletesOngsAndResources() {
		String suffix = UUID.randomUUID().toString();
		var ong = ongService.create(new OngService.OngRequest("ONG " + suffix, "Asistencia"));

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

		var updatedOng = ongService.update(ong.id(), new OngService.OngRequest("ONG actualizada " + suffix, "Salud"));
		assertEquals("Salud", updatedOng.tipo());
		assertEquals(updatedOng.id(), ongService.findById(ong.id()).id());

		recursoService.delete(recurso.id());
		ongService.delete(ong.id());
		assertEquals(HttpStatus.NOT_FOUND,
				assertThrows(ResponseStatusException.class, () -> ongService.findById(ong.id())).getStatusCode());
	}

	@Test
	void rejectsInvalidResourceQuantities() {
		var ong = ongService.create(new OngService.OngRequest("ONG " + UUID.randomUUID(), "Asistencia"));
		var error = assertThrows(ResponseStatusException.class,
				() -> recursoService.create(new RecursoService.RecursoRequest("Alimentos", 2, 3, ong.id())));
		assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
		ongService.delete(ong.id());
	}
}
