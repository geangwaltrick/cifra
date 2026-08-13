package com.cifra.comum;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Tokens opacos (verificacao de e-mail, refresh) e seu hash de armazenamento.
 *
 * <p>Regra: o valor em claro sai uma unica vez, na resposta ou no e-mail. O
 * banco so guarda o SHA-256. Vazou o dump, nao vazou sessao.
 *
 * <p>SHA-256 sem sal e adequado aqui e nao seria para senha: o token tem 256
 * bits de entropia aleatoria, entao nao existe dicionario para atacar.
 */
public final class TokensOpacos {

	private static final SecureRandom ALEATORIO = new SecureRandom();

	private static final int BYTES_DE_ENTROPIA = 32;

	private TokensOpacos() {
	}

	public static String gerar() {
		byte[] bytes = new byte[BYTES_DE_ENTROPIA];
		ALEATORIO.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	public static String hashDe(String token) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] resumo = digest.digest(token.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(resumo);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 indisponivel nesta JVM.", ex);
		}
	}

}
