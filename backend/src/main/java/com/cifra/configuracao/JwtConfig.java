package com.cifra.configuracao;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import java.nio.charset.StandardCharsets;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Emissao e validacao de JWT com HMAC-SHA256, usando o proprio Spring Security.
 * Nao ha biblioteca de terceiro no caminho do token.
 */
@Configuration(proxyBeanMethods = false)
public class JwtConfig {

	private static final int MINIMO_DE_BYTES = 32;

	@Bean
	SecretKey chaveDeAssinatura(PropriedadesDoCifra propriedades) {
		byte[] segredo = propriedades.jwt().segredo().getBytes(StandardCharsets.UTF_8);

		if (segredo.length < MINIMO_DE_BYTES) {
			// HS256 exige 256 bits. Falhar na subida e melhor do que emitir
			// token fraco e descobrir em producao.
			throw new IllegalStateException(
					"cifra.jwt.segredo precisa de ao menos %d bytes; recebeu %d."
							.formatted(MINIMO_DE_BYTES, segredo.length));
		}

		return new SecretKeySpec(segredo, "HmacSHA256");
	}

	@Bean
	JwtEncoder codificadorDeJwt(SecretKey chave) {
		return new NimbusJwtEncoder(new ImmutableSecret<>(chave));
	}

	@Bean
	JwtDecoder decodificadorDeJwt(SecretKey chave) {
		return NimbusJwtDecoder.withSecretKey(chave).build();
	}

}
