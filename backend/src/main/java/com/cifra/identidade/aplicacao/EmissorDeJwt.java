package com.cifra.identidade.aplicacao;

import java.time.Duration;
import java.time.Instant;

import com.cifra.configuracao.PropriedadesDoCifra;
import com.cifra.identidade.dominio.Usuario;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/** Emite o access token. Curto de proposito: quem dura e o refresh. */
@Component
public class EmissorDeJwt {

	private final JwtEncoder codificador;

	private final Duration validade;

	public EmissorDeJwt(JwtEncoder codificador, PropriedadesDoCifra propriedades) {
		this.codificador = codificador;
		this.validade = propriedades.jwt().validadeDoAcesso();
	}

	public AccessToken emitirPara(Usuario usuario) {
		Instant agora = Instant.now();
		Instant expiracao = agora.plus(this.validade);

		JwtClaimsSet reivindicacoes = JwtClaimsSet.builder()
			.issuer("cifra")
			.issuedAt(agora)
			.expiresAt(expiracao)
			.subject(String.valueOf(usuario.getId()))
			.claim("email", usuario.getEmail())
			.claim("nome", usuario.getNome())
			.build();

		// O header precisa ser explicito. Sem ele o NimbusJwtEncoder assume
		// RS256, nao acha nenhuma chave RSA no segredo HMAC e falha com
		// "Failed to select a JWK signing key" -- em todo login bem-sucedido.
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();

		String valor = this.codificador.encode(JwtEncoderParameters.from(header, reivindicacoes)).getTokenValue();
		return new AccessToken(valor, this.validade.toSeconds());
	}

	public record AccessToken(String valor, long expiraEmSegundos) {
	}

}
