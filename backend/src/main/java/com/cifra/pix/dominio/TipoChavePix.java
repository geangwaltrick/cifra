package com.cifra.pix.dominio;

public enum TipoChavePix {

	/** Precisa ser o CPF do proprio titular. */
	CPF,

	/** Precisa ser o e-mail cadastrado do titular. */
	EMAIL,

	/** Somente digitos, com DDD. */
	TELEFONE,

	/** Gerada pelo sistema; o valor informado pelo cliente e ignorado. */
	ALEATORIA;

	public boolean ehGeradaPeloSistema() {
		return this == ALEATORIA;
	}

}
