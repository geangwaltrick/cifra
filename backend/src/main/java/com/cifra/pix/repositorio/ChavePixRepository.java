package com.cifra.pix.repositorio;

import java.util.List;
import java.util.Optional;

import com.cifra.pix.dominio.ChavePix;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ChavePixRepository extends JpaRepository<ChavePix, Long> {

	Optional<ChavePix> findByValor(String valor);

	List<ChavePix> findByContaIdOrderByCriadoEmAsc(Long contaId);

	long countByContaId(Long contaId);

}
