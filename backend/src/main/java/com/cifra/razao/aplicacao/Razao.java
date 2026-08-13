package com.cifra.razao.aplicacao;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.function.Supplier;

import com.cifra.comum.ProblemaDeNegocio;
import com.cifra.identidade.dominio.Conta;
import com.cifra.identidade.repositorio.ContaRepository;
import com.cifra.razao.dominio.Transacao;
import com.cifra.razao.dominio.TipoTransacao;
import com.cifra.razao.repositorio.TransacaoRepository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Porta de entrada de toda movimentacao de dinheiro.
 *
 * <p>Repare no que esta classe <em>nao</em> tem: {@code @Transactional}. E de
 * proposito. Quando duas requisicoes chegam com a mesma chave de idempotencia,
 * uma delas leva violacao de unicidade e precisa entao <em>ler</em> a transacao
 * que a outra gravou. Se essa leitura acontecesse dentro da transacao que
 * acabou de falhar, ela encontraria a transacao marcada para rollback e
 * estouraria de novo. Aqui o trabalho transacional vive em
 * {@link MotorDoRazao}, e a recuperacao roda fora dele.
 */
@Service
public class Razao {

	private final MotorDoRazao motor;

	private final TransacaoRepository transacoes;

	private final ContaRepository contas;

	private final ContaDeLiquidacao liquidacao;

	public Razao(MotorDoRazao motor, TransacaoRepository transacoes, ContaRepository contas,
			ContaDeLiquidacao liquidacao) {
		this.motor = motor;
		this.transacoes = transacoes;
		this.contas = contas;
		this.liquidacao = liquidacao;
	}

	/** Dinheiro entra: debita a liquidacao, credita o cliente. */
	public Transacao depositar(Long contaId, BigDecimal valor, String idempotencyKey, String descricao) {
		BigDecimal montante = normalizar(valor);
		exigirContaMovimentavel(contaId);

		return comIdempotencia(idempotencyKey, () -> this.motor.lancar(TipoTransacao.DEPOSITO,
				this.liquidacao.id(), contaId, montante, descricao, idempotencyKey));
	}

	/** Dinheiro sai: debita o cliente, credita a liquidacao. */
	public Transacao sacar(Long contaId, BigDecimal valor, String idempotencyKey, String descricao) {
		BigDecimal montante = normalizar(valor);
		exigirContaMovimentavel(contaId);

		return comIdempotencia(idempotencyKey, () -> this.motor.lancar(TipoTransacao.SAQUE,
				contaId, this.liquidacao.id(), montante, descricao, idempotencyKey));
	}

	/** Dinheiro muda de dono: o total do sistema nao se altera. */
	public Transacao transferir(Long origemId, Long destinoId, BigDecimal valor, String idempotencyKey,
			String descricao) {
		BigDecimal montante = normalizar(valor);

		if (origemId.equals(destinoId)) {
			throw ProblemaDeNegocio.requisicaoInvalida("transferencia-para-si",
					"Origem e destino sao a mesma conta.");
		}
		exigirContaMovimentavel(origemId);
		exigirContaMovimentavel(destinoId);

		return comIdempotencia(idempotencyKey, () -> this.motor.lancar(TipoTransacao.TRANSFERENCIA,
				origemId, destinoId, montante, descricao, idempotencyKey));
	}

	/**
	 * Executa a operacao uma unica vez por chave.
	 *
	 * <p>A checagem antecipada resolve o caso comum -- cliente reenviando depois
	 * de um timeout. A garantia de verdade e a unique constraint: em duas
	 * requisicoes realmente simultaneas, ambas passam pela checagem, o banco
	 * aceita uma e recusa a outra, e a recusada devolve o resultado da primeira.
	 */
	private Transacao comIdempotencia(String chave, Supplier<Transacao> operacao) {
		if (chave != null) {
			Transacao jaExecutada = this.transacoes.findByIdempotencyKey(chave).orElse(null);
			if (jaExecutada != null) {
				return jaExecutada;
			}
		}

		try {
			return operacao.get();
		}
		catch (DataIntegrityViolationException ex) {
			if (chave == null) {
				throw ex;
			}
			// Se a chave nao esta la, a violacao foi de outra constraint e nao
			// tem nada a ver com idempotencia: propaga.
			return this.transacoes.findByIdempotencyKey(chave).orElseThrow(() -> ex);
		}
	}

	private void exigirContaMovimentavel(Long contaId) {
		Conta conta = this.contas.findById(contaId)
			.orElseThrow(() -> ProblemaDeNegocio.naoEncontrado("conta-nao-encontrada",
					"Conta nao encontrada."));

		if (!conta.getTipo().ehDeCliente()) {
			throw ProblemaDeNegocio.requisicaoInvalida("conta-nao-movimentavel",
					"Esta conta nao aceita movimentacao direta.");
		}
		if (!conta.getStatus().aceitaMovimentacao()) {
			throw ProblemaDeNegocio.requisicaoInvalida("conta-bloqueada",
					"Conta bloqueada ou encerrada.");
		}
	}

	/**
	 * Dinheiro tem duas casas decimais e ponto final.
	 *
	 * <p>{@code UNNECESSARY} faz o {@code setScale} estourar em vez de
	 * arredondar: um pedido de R$ 10,999 e recusado, nao silenciosamente virado
	 * em R$ 11,00. Arredondar por conta propria e como se perde centavo em
	 * sistema financeiro.
	 */
	private static BigDecimal normalizar(BigDecimal valor) {
		if (valor == null || valor.signum() <= 0) {
			throw ProblemaDeNegocio.requisicaoInvalida("valor-invalido", "O valor deve ser maior que zero.");
		}

		try {
			return valor.setScale(2, RoundingMode.UNNECESSARY);
		}
		catch (ArithmeticException ex) {
			throw ProblemaDeNegocio.requisicaoInvalida("valor-com-fracao-de-centavo",
					"O valor nao pode ter mais de duas casas decimais.");
		}
	}

}
