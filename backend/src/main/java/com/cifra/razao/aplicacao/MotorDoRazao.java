package com.cifra.razao.aplicacao;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.cifra.comum.ProblemaDeNegocio;
import com.cifra.razao.dominio.Saldo;
import com.cifra.razao.dominio.Transacao;
import com.cifra.razao.dominio.TipoTransacao;
import com.cifra.razao.repositorio.SaldoRepository;
import com.cifra.razao.repositorio.TransacaoRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * O unico lugar do sistema que escreve no razao.
 *
 * <p>Toda movimentacao passa por {@link #lancar}: trava as contas em ordem,
 * confere o saldo, grava o par de lancamentos e so entao atualiza a projecao.
 * Nao existe caminho alternativo -- e por isso que o invariante se sustenta.
 */
@Service
public class MotorDoRazao {

	private final TransacaoRepository transacoes;

	private final SaldoRepository saldos;

	public MotorDoRazao(TransacaoRepository transacoes, SaldoRepository saldos) {
		this.transacoes = transacoes;
		this.saldos = saldos;
	}

	@Transactional
	public Transacao lancar(TipoTransacao tipo, Long contaDebitada, Long contaCreditada, BigDecimal valor,
			String descricao, String idempotencyKey) {

		Map<Long, Saldo> travados = travarEmOrdem(contaDebitada, contaCreditada);
		Saldo origem = travados.get(contaDebitada);
		Saldo destino = travados.get(contaCreditada);

		if (!origem.comporta(valor.negate())) {
			throw ProblemaDeNegocio.requisicaoInvalida("saldo-insuficiente",
					"Saldo insuficiente para esta operacao.");
		}

		Transacao transacao = new Transacao(tipo, valor, descricao, idempotencyKey);
		transacao.lancarPar(contaDebitada, contaCreditada, valor);
		transacao.liquidar();
		this.transacoes.save(transacao);

		origem.aplicar(valor.negate());
		destino.aplicar(valor);

		return transacao;
	}

	/**
	 * Trava as duas contas em ordem crescente de id.
	 *
	 * <p>A ordem e o que elimina o deadlock: se A para B travasse A primeiro e
	 * B para A travasse B primeiro, as duas transacoes ficariam esperando uma a
	 * outra. Ordenando, quem chega depois espera na mesma primeira linha.
	 */
	private Map<Long, Saldo> travarEmOrdem(Long primeira, Long segunda) {
		List<Long> ids = Stream.of(primeira, segunda).distinct().sorted().toList();

		Map<Long, Saldo> encontrados = new HashMap<>();
		for (Saldo saldo : this.saldos.travarPorContas(ids)) {
			encontrados.put(saldo.getContaId(), saldo);
		}

		for (Long id : ids) {
			if (!encontrados.containsKey(id)) {
				throw ProblemaDeNegocio.naoEncontrado("conta-sem-saldo",
						"Conta %d nao possui registro de saldo.".formatted(id));
			}
		}

		return encontrados;
	}

}
