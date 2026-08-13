package com.cifra.razao.aplicacao;

import com.cifra.identidade.dominio.TipoConta;
import com.cifra.identidade.repositorio.ContaRepository;

import org.springframework.stereotype.Component;

/**
 * A fronteira contabil entre o Cifra e o mundo.
 *
 * <p>Depositar credita o cliente e debita esta conta; sacar faz o contrario.
 * Com isso um deposito continua somando zero, e o saldo desta conta e, a
 * qualquer momento, o simetrico exato da soma de todas as contas de cliente --
 * o que da uma segunda forma de conferir os livros.
 */
@Component
public class ContaDeLiquidacao {

	private final ContaRepository contas;

	private volatile Long id;

	public ContaDeLiquidacao(ContaRepository contas) {
		this.contas = contas;
	}

	public Long id() {
		Long conhecido = this.id;
		if (conhecido != null) {
			return conhecido;
		}

		// Criada pela migration V3; se sumiu, o esquema esta quebrado e nao
		// adianta seguir movimentando dinheiro.
		Long encontrado = this.contas.findFirstByTipo(TipoConta.LIQUIDACAO)
			.orElseThrow(() -> new IllegalStateException(
					"Conta de liquidacao ausente. A migration V3 nao foi aplicada?"))
			.getId();

		this.id = encontrado;
		return encontrado;
	}

}
