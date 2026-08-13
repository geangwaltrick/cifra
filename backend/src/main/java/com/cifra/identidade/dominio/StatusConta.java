package com.cifra.identidade.dominio;

public enum StatusConta {

	ATIVA,

	BLOQUEADA,

	ENCERRADA;

	public boolean aceitaMovimentacao() {
		return this == ATIVA;
	}

}
