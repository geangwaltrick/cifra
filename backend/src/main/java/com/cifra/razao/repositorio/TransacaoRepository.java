package com.cifra.razao.repositorio;

import java.util.Optional;

import com.cifra.razao.dominio.Transacao;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TransacaoRepository extends JpaRepository<Transacao, Long> {

	Optional<Transacao> findByIdempotencyKey(String idempotencyKey);

	boolean existsByEstornoDeId(Long transacaoId);

}
