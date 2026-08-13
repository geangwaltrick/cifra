package com.cifra.razao.aplicacao;

import java.math.BigDecimal;

import com.cifra.razao.dominio.TipoTransacao;

/**
 * O pedido de uma movimentacao, pronto para o motor executar.
 *
 * <p>Existe para que a assinatura de {@code lancar} nao vire uma fila de sete
 * parametros posicionais, onde trocar origem por destino compila em silencio.
 */
public record Movimento(TipoTransacao tipo, Long contaDebitada, Long contaCreditada, BigDecimal valor,
		String descricao, String idempotencyKey, boolean consomeLimiteDiario) {

	/** Dinheiro entrando pela fronteira: nao consome limite de saida. */
	public static Movimento entrada(TipoTransacao tipo, Long liquidacao, Long destino, BigDecimal valor,
			String descricao, String chave) {
		return new Movimento(tipo, liquidacao, destino, valor, descricao, chave, false);
	}

	/** Saida por vontade do titular: consome limite. */
	public static Movimento saida(TipoTransacao tipo, Long origem, Long destino, BigDecimal valor,
			String descricao, String chave) {
		return new Movimento(tipo, origem, destino, valor, descricao, chave, true);
	}

}
