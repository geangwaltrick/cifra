package com.cifra.identidade.dominio;

/** CPF que nao passou na validacao dos digitos verificadores. */
public class CpfInvalidoException extends IllegalArgumentException {

	public CpfInvalidoException(String mensagem) {
		super(mensagem);
	}

}
