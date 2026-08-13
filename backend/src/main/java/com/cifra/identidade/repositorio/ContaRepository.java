package com.cifra.identidade.repositorio;

import java.util.Optional;

import com.cifra.identidade.dominio.Conta;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ContaRepository extends JpaRepository<Conta, Long> {

	Optional<Conta> findByUsuarioId(Long usuarioId);

	boolean existsByAgenciaAndNumero(String agencia, String numero);

}
