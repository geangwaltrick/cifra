package com.cifra.razao.dominio;

public enum StatusTransacao {

	/** Aceita, ainda nao lancada. Usada pelas liquidacoes assincronas. */
	PENDENTE,

	LIQUIDADA,

	REJEITADA,

	/** Continua liquidada; um estorno simetrico foi lancado depois. */
	ESTORNADA;

	public boolean podeSerEstornada() {
		return this == LIQUIDADA;
	}

}
