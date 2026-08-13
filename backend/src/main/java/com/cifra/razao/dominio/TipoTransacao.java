package com.cifra.razao.dominio;

public enum TipoTransacao {

	DEPOSITO,

	SAQUE,

	/** Entre contas do Cifra, endereçada por agencia e numero. */
	TRANSFERENCIA,

	/** Mesma mecanica da transferencia; muda so como o destino foi endereçado. */
	PIX,

	ESTORNO

}
