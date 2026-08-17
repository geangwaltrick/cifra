package com.cifra.cartao.dominio;

public enum StatusCartao {

	ATIVO,

	/** Bloqueio temporario: o titular pode desfazer. */
	BLOQUEADO,

	/** Definitivo. Nao volta atras -- exige emitir outro cartao. */
	CANCELADO;

	public boolean podeSerDesbloqueado() {
		return this == BLOQUEADO;
	}

}
