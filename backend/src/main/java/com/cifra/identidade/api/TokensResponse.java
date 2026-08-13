package com.cifra.identidade.api;

import com.cifra.identidade.aplicacao.Autenticacao;

public record TokensResponse(String accessToken, String refreshToken, String tipo, long expiraEmSegundos) {

	public static TokensResponse de(Autenticacao.ParDeTokens par) {
		return new TokensResponse(par.accessToken(), par.refreshToken(), "Bearer", par.expiraEmSegundos());
	}

}
