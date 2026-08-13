package com.cifra.razao.repositorio;

import java.math.BigDecimal;
import java.util.List;

import com.cifra.razao.dominio.Lancamento;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LancamentoRepository extends JpaRepository<Lancamento, Long> {

	List<Lancamento> findByContaIdOrderByCriadoEmDesc(Long contaId);

	/** Saldo verdadeiro da conta: a soma do razao, sem passar pela projecao. */
	@Query("select coalesce(sum(l.valor), 0) from Lancamento l where l.contaId = :conta")
	BigDecimal somarPorConta(@Param("conta") Long conta);

	/** Soma de todo o razao. Tem que ser zero, sempre. */
	@Query("select coalesce(sum(l.valor), 0) from Lancamento l")
	BigDecimal somarTudo();

	/**
	 * Contas cuja projecao de saldo divergiu do razao. Zero linhas e o resultado
	 * esperado; qualquer linha aqui e incidente.
	 */
	@Query(value = """
			select s.conta_id      as contaId,
			       s.saldo         as projetado,
			       coalesce(r.total, 0) as razao
			  from saldos s
			  left join (select conta_id, sum(valor) as total
			               from lancamentos
			              group by conta_id) r on r.conta_id = s.conta_id
			 where s.saldo <> coalesce(r.total, 0)
			""", nativeQuery = true)
	List<DivergenciaDeSaldo> divergencias();

	/** Transacoes cujos lancamentos nao somam zero. Deve vir sempre vazio. */
	@Query(value = """
			select transacao_id as transacaoId, sum(valor) as desbalanco
			  from lancamentos
			 group by transacao_id
			having sum(valor) <> 0
			""", nativeQuery = true)
	List<TransacaoDesbalanceada> desbalanceadas();

	interface DivergenciaDeSaldo {

		Long getContaId();

		BigDecimal getProjetado();

		BigDecimal getRazao();

	}

	interface TransacaoDesbalanceada {

		Long getTransacaoId();

		BigDecimal getDesbalanco();

	}

}
