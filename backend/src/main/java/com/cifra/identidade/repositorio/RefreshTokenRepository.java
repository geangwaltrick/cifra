package com.cifra.identidade.repositorio;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.cifra.identidade.dominio.RefreshToken;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

	Optional<RefreshToken> findByTokenHash(String tokenHash);

	/** Derruba a familia inteira quando um token revogado reaparece. */
	@Modifying
	@Query("""
			update RefreshToken rt
			   set rt.revogadoEm = :momento
			 where rt.familia = :familia
			   and rt.revogadoEm is null
			""")
	int revogarFamilia(@Param("familia") UUID familia, @Param("momento") Instant momento);

}
