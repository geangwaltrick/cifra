package com.cifra.razao.repositorio;

import java.math.BigDecimal;
import java.time.Instant;

import com.cifra.razao.dominio.Lancamento;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExtratoRepository extends JpaRepository<Lancamento, Long> {

	/**
	 * Extrato da conta, com saldo corrente linha a linha.
	 *
	 * <p>A ordem das operacoes e o ponto todo desta consulta. A window function
	 * roda na subconsulta interna, sobre o historico <em>inteiro</em> da conta;
	 * os filtros de periodo e tipo so entram depois, do lado de fora. Se o
	 * filtro fosse aplicado antes, o saldo corrente do primeiro dia do recorte
	 * comecaria do zero em vez de partir do que a conta ja tinha -- e o extrato
	 * mostraria numeros que nunca existiram.
	 *
	 * <p>Os apelidos vao entre aspas porque o Postgres rebaixa identificador
	 * sem aspas para minusculas, e a projecao casa pelo nome exato da coluna.
	 *
	 * <p>A contraparte e o outro lado do mesmo fato: em uma transferencia, quem
	 * recebeu; em um deposito, a conta de liquidacao.
	 */
	@Query(value = """
			select * from (
			  select l.id        as "id",
			         l.criado_em as "data",
			         t.id        as "transacaoId",
			         t.tipo      as "tipo",
			         t.status    as "status",
			         t.descricao as "descricao",
			         l.valor     as "valor",
			         sum(l.valor) over (order by l.criado_em, l.id) as "saldoApos",
			         (select c.agencia || ' / ' || c.numero
			            from lancamentos o
			            join contas c on c.id = o.conta_id
			           where o.transacao_id = t.id
			             and o.conta_id <> l.conta_id
			           limit 1) as "contraparte"
			    from lancamentos l
			    join transacoes t on t.id = l.transacao_id
			   where l.conta_id = :conta
			) linha
			 where (cast(:de   as timestamptz) is null or linha."data" >= cast(:de  as timestamptz))
			   and (cast(:ate  as timestamptz) is null or linha."data" <= cast(:ate as timestamptz))
			   and (cast(:tipo as varchar)     is null or linha."tipo"  = cast(:tipo as varchar))
			 order by linha."data" desc, linha."id" desc
			""",
			countQuery = """
					select count(*)
					  from lancamentos l
					  join transacoes t on t.id = l.transacao_id
					 where l.conta_id = :conta
					   and (cast(:de   as timestamptz) is null or l.criado_em >= cast(:de  as timestamptz))
					   and (cast(:ate  as timestamptz) is null or l.criado_em <= cast(:ate as timestamptz))
					   and (cast(:tipo as varchar)     is null or t.tipo       = cast(:tipo as varchar))
					""",
			nativeQuery = true)
	Page<LinhaDoExtrato> extrato(@Param("conta") Long conta, @Param("de") Instant de, @Param("ate") Instant ate,
			@Param("tipo") String tipo, Pageable pagina);

	interface LinhaDoExtrato {

		Long getId();

		Instant getData();

		Long getTransacaoId();

		String getTipo();

		String getStatus();

		String getDescricao();

		BigDecimal getValor();

		BigDecimal getSaldoApos();

		String getContraparte();

	}

}
