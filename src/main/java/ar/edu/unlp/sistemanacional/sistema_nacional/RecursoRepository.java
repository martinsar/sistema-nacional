package ar.edu.unlp.sistemanacional.sistema_nacional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RecursoRepository extends JpaRepository<Recurso, Long> {
	boolean existsByOngId(Long ongId);
}
