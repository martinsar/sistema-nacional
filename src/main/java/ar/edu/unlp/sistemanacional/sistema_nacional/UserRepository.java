package ar.edu.unlp.sistemanacional.sistema_nacional;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<ApplicationUser, Long> {
	Optional<ApplicationUser> findByUsername(String username);

	boolean existsByUsername(String username);
}