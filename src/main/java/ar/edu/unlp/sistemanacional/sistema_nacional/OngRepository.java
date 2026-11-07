package ar.edu.unlp.sistemanacional.sistema_nacional;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OngRepository extends JpaRepository<Ong, Long> {

    Optional<Ong> findByNombre(String nombre);

    Optional<Ong> findByCodigoOng(String codigoOng);

    boolean existsByCodigoOng(String codigoOng);

    List<Ong> findAllByCodigoOngIsNull();

}
