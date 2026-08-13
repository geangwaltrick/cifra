package com.cifra.comum;

import org.springframework.http.HttpStatus;

/**
 * Erro de negocio com identidade estavel.
 *
 * <p>O {@code tipo} e um slug que vira URI no corpo da resposta e nunca muda --
 * e nele que o front decide o que mostrar, nao no texto da mensagem.
 */
public class ProblemaDeNegocio extends RuntimeException {

	private final String tipo;

	private final HttpStatus status;

	public ProblemaDeNegocio(String tipo, HttpStatus status, String mensagem) {
		super(mensagem);
		this.tipo = tipo;
		this.status = status;
	}

	public static ProblemaDeNegocio conflito(String tipo, String mensagem) {
		return new ProblemaDeNegocio(tipo, HttpStatus.CONFLICT, mensagem);
	}

	public static ProblemaDeNegocio requisicaoInvalida(String tipo, String mensagem) {
		return new ProblemaDeNegocio(tipo, HttpStatus.BAD_REQUEST, mensagem);
	}

	public static ProblemaDeNegocio naoAutorizado(String tipo, String mensagem) {
		return new ProblemaDeNegocio(tipo, HttpStatus.UNAUTHORIZED, mensagem);
	}

	public static ProblemaDeNegocio naoEncontrado(String tipo, String mensagem) {
		return new ProblemaDeNegocio(tipo, HttpStatus.NOT_FOUND, mensagem);
	}

	public static ProblemaDeNegocio excessoDeTentativas(String mensagem) {
		return new ProblemaDeNegocio("excesso-de-tentativas", HttpStatus.TOO_MANY_REQUESTS, mensagem);
	}

	public String getTipo() {
		return this.tipo;
	}

	public HttpStatus getStatus() {
		return this.status;
	}

}
