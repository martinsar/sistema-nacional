package ar.edu.unlp.sistemanacional.sistema_nacional;

import java.util.List;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface RecursoRepository extends JpaRepository<Recurso, Long> {
	boolean existsByOngId(Long ongId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	List<Recurso> findByTipoIgnoreCaseAndOng_NombreIgnoreCaseOrderByIdAsc(String tipo, String nombre);
}
