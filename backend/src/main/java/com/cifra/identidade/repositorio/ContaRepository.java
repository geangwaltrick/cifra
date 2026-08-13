package com.cifra.identidade.repositorio;

import java.util.Optional;

import com.cifra.identidade.dominio.Conta;
import com.cifra.identidade.dominio.TipoConta;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContaRepository extends JpaRepository<Conta, Long> {

	/**
	 * Traz o titular junto.
	 *
	 * <p>Quem chama isto sao controllers, fora de transacao. Sem o grafo, o
	 * {@code getUsuario()} de uma conta ja destacada estoura -- foi o que
	 * quebrou o registro de chave PIX por CPF e por e-mail, que precisam
	 * conferir os dados do titular.
	 */
	@EntityGraph(attributePaths = "usuario")
	Optional<Conta> findByUsuarioId(Long usuarioId);

	boolean existsByAgenciaAndNumero(String agencia, String numero);

	Optional<Conta> findFirstByTipo(TipoConta tipo);

	Optional<Conta> findByAgenciaAndNumero(String agencia, String numero);

}
