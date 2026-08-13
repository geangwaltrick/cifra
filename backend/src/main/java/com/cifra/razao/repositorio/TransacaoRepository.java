package com.cifra.razao.repositorio;

import java.util.Optional;

import com.cifra.razao.dominio.Transacao;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransacaoRepository extends JpaRepository<Transacao, Long> {

	/**
	 * Traz os lancamentos junto, de proposito.
	 *
	 * <p>Quem chama isto e o caminho de idempotencia, que roda fora de
	 * transacao. Com {@code open-in-view: false} -- e ele esta desligado --
	 * a colecao preguicosa estouraria ao ser serializada na resposta.
	 * Justamente na segunda chamada com a mesma chave, que e o caso que a
	 * idempotencia existe para atender.
	 */
	@EntityGraph(attributePaths = "lancamentos")
	Optional<Transacao> findByIdempotencyKey(String idempotencyKey);

	boolean existsByEstornoDeId(Long transacaoId);

}
