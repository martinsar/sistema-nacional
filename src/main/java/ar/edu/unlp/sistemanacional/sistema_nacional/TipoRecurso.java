package ar.edu.unlp.sistemanacional.sistema_nacional;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

// [Entrega final] Catálogo único de tipos de recurso con código estable, compartido con la app local
public enum TipoRecurso {

	ALIMENTOS("Raciones de alimento", "Alimentos"),
	AGUA("Agua potable"),
	MEDICAMENTOS("Medicamentos"),
	PARAMEDICOS("Paramédicos"),
	BOMBEROS("Bomberos"),
	RESCATISTAS("Rescatistas"),
	FRAZADAS("Frazadas"),
	CARPAS("Carpas"),
	GENERADORES("Generadores eléctricos"),
	VOLUNTARIOS("Voluntarios"),
	ROPA_ABRIGO("Ropa de abrigo");

	private final String nombre;
	private final List<String> alias;

	TipoRecurso(String nombre, String... alias) {
		this.nombre = nombre;
		this.alias = List.of(alias);
	}

	public String getCodigo() {
		return name();
	}

	public String getNombre() {
		return nombre;
	}

	// [Entrega final] Se devuelve el código canónico para un código, nombre o alias; vacío si no pertenece al catálogo
	public static Optional<String> normalizar(String texto) {
		if (texto == null) {
			return Optional.empty();
		}
		String buscado = clave(texto);
		if (buscado.isEmpty()) {
			return Optional.empty();
		}
		return Arrays.stream(values())
				.filter(t -> clave(t.name()).equals(buscado) || clave(t.nombre).equals(buscado)
						|| t.alias.stream().anyMatch(a -> clave(a).equals(buscado)))
				.map(TipoRecurso::getCodigo)
				.findFirst();
	}

	// [Entrega final] Se exige un tipo del catálogo; si no, responde 400
	public static String exigir(String texto) {
		return normalizar(texto).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
				"Tipo de recurso desconocido: " + texto));
	}

	// Sin tildes, sin mayúsculas, con "_" como espacio y sin espacios sobrantes
	private static String clave(String texto) {
		String sinTildes = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		return sinTildes.replace('_', ' ').trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
	}
}
