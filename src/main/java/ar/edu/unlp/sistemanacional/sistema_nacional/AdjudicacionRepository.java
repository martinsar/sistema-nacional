package ar.edu.unlp.sistemanacional.sistema_nacional;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AdjudicacionRepository extends JpaRepository<Adjudicacion, Long> {

	List<Adjudicacion> findByConvocatoriaIdAndLiberadaFalseOrderByIdAsc(Long convocatoriaId);

	List<Adjudicacion> findByConvocatoriaIdOrderByIdAsc(Long convocatoriaId);
}
