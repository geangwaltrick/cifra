package com.cifra.identidade.dominio;

public enum StatusUsuario {

	/** Cadastrado, mas ainda nao clicou no link do e-mail. Nao pode autenticar. */
	PENDENTE_VERIFICACAO,

	ATIVO,

	BLOQUEADO,

	ENCERRADO;

	public boolean podeAutenticar() {
		return this == ATIVO;
	}

}
