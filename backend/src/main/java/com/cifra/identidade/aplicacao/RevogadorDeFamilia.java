package com.cifra.identidade.aplicacao;

import java.time.Instant;
import java.util.UUID;

import com.cifra.identidade.repositorio.RefreshTokenRepository;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Derruba uma familia de refresh tokens em transacao propria.
 *
 * <p>Existe como componente separado por um motivo especifico: quem detecta o
 * reuso lanca excecao logo em seguida, e uma excecao faz rollback da transacao
 * corrente. Se a revogacao rodasse junto, o rollback desfaria exatamente a
 * medida de seguranca que acabou de ser tomada -- e o token roubado
 * continuaria valido. REQUIRES_NEW commita antes de a excecao subir.
 *
 * <p>Autoinvocacao nao funcionaria: o proxy do Spring so intercepta chamadas
 * que atravessam a fronteira do bean.
 */
@Component
public class RevogadorDeFamilia {

	private final RefreshTokenRepository refreshTokens;

	public RevogadorDeFamilia(RefreshTokenRepository refreshTokens) {
		this.refreshTokens = refreshTokens;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public int revogar(UUID familia, Instant momento) {
		return this.refreshTokens.revogarFamilia(familia, momento);
	}

}
