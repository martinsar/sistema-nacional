package ar.edu.unlp.sistemanacional.sistema_nacional;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// [Entrega final] Semilla de datos de prueba: opt-in (app.seed.enabled=true) e idempotente
@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class SemillaDatosRunner implements ApplicationRunner {

	private static final int STOCK_INICIAL = 1000;

	private record OngSemilla(String codigo, String nombre, List<TipoRecurso> tipos) {
	}

	private static final List<OngSemilla> ONGS = List.of(
			new OngSemilla("ONG-CARITAS", "Cáritas", List.of(TipoRecurso.ALIMENTOS, TipoRecurso.AGUA,
					TipoRecurso.FRAZADAS, TipoRecurso.ROPA_ABRIGO, TipoRecurso.CARPAS)),
			new OngSemilla("ONG-CRUZ-ROJA", "Cruz Roja Argentina", List.of(TipoRecurso.PARAMEDICOS,
					TipoRecurso.MEDICAMENTOS, TipoRecurso.AGUA, TipoRecurso.ALIMENTOS, TipoRecurso.VOLUNTARIOS)),
			new OngSemilla("ONG-BOMBEROS-LP", "Bomberos Voluntarios La Plata", List.of(TipoRecurso.BOMBEROS,
					TipoRecurso.RESCATISTAS, TipoRecurso.GENERADORES)),
			new OngSemilla("ONG-MEDICOS-MUNDO", "Médicos del Mundo",
					List.of(TipoRecurso.MEDICAMENTOS, TipoRecurso.PARAMEDICOS)),
			new OngSemilla("ONG-DEFENSA-CIVIL", "Defensa Civil Provincial", List.of(TipoRecurso.RESCATISTAS,
					TipoRecurso.CARPAS, TipoRecurso.GENERADORES, TipoRecurso.VOLUNTARIOS)));

	private final OngRepository ongRepository;
	private final RecursoRepository recursoRepository;

	public SemillaDatosRunner(OngRepository ongRepository, RecursoRepository recursoRepository) {
		this.ongRepository = ongRepository;
		this.recursoRepository = recursoRepository;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		sembrar();
	}

	// [Entrega final] Si la ONG ya existe por codigo_ong no se toca; devuelve la cantidad de ONGs creadas
	@Transactional
	public int sembrar() {
		int creadas = 0;
		for (OngSemilla semilla : ONGS) {
			if (ongRepository.existsByCodigoOng(semilla.codigo())) {
				continue;
			}
			List<String> codigos = new ArrayList<>(semilla.tipos().stream().map(TipoRecurso::getCodigo).toList());
			Ong ong = ongRepository.save(new Ong(semilla.codigo(), semilla.nombre(), codigos));
			for (String codigo : codigos) {
				recursoRepository.save(new Recurso(codigo, STOCK_INICIAL, 0, ong));
			}
			creadas++;
		}
		return creadas;
	}
}
