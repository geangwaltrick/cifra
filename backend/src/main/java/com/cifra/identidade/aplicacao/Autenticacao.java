package com.cifra.identidade.aplicacao;

import java.time.Instant;
import java.util.UUID;

import com.cifra.comum.ProblemaDeNegocio;
import com.cifra.comum.TokensOpacos;
import com.cifra.configuracao.PropriedadesDoCifra;
import com.cifra.identidade.dominio.RefreshToken;
import com.cifra.identidade.dominio.TokenDeVerificacao;
import com.cifra.identidade.dominio.Usuario;
import com.cifra.identidade.repositorio.RefreshTokenRepository;
import com.cifra.identidade.repositorio.TokenDeVerificacaoRepository;
import com.cifra.identidade.repositorio.UsuarioRepository;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class Autenticacao {

	private final UsuarioRepository usuarios;

	private final RefreshTokenRepository refreshTokens;

	private final TokenDeVerificacaoRepository verificacoes;

	private final PasswordEncoder senhas;

	private final EmissorDeJwt emissor;

	private final LimitadorDeTentativas limitador;

	private final RevogadorDeFamilia revogador;

	private final PropriedadesDoCifra propriedades;

	/**
	 * Hash descartavel comparado quando o e-mail nao existe. Sem ele, a resposta
	 * volta rapido demais para e-mail inexistente e lenta para e-mail real --
	 * o proprio tempo de resposta viraria um oraculo de quem tem conta aqui.
	 */
	private final String hashDeReferencia;

	public Autenticacao(UsuarioRepository usuarios, RefreshTokenRepository refreshTokens,
			TokenDeVerificacaoRepository verificacoes, PasswordEncoder senhas, EmissorDeJwt emissor,
			LimitadorDeTentativas limitador, RevogadorDeFamilia revogador, PropriedadesDoCifra propriedades) {
		this.usuarios = usuarios;
		this.refreshTokens = refreshTokens;
		this.verificacoes = verificacoes;
		this.senhas = senhas;
		this.emissor = emissor;
		this.limitador = limitador;
		this.revogador = revogador;
		this.propriedades = propriedades;
		this.hashDeReferencia = senhas.encode(UUID.randomUUID().toString());
	}

	@Transactional
	public ParDeTokens entrar(String emailInformado, String senha, String origem) {
		String email = Usuario.normalizarEmail(emailInformado);
		String chave = email + "|" + origem;

		this.limitador.registrarTentativa(chave);

		Usuario usuario = this.usuarios.findByEmail(email).orElse(null);
		boolean senhaConfere = (usuario != null)
				? this.senhas.matches(senha, usuario.getSenhaHash())
				: this.senhas.matches(senha, this.hashDeReferencia);

		if (usuario == null || !senhaConfere) {
			// Mesma resposta para e-mail inexistente e senha errada: nao ha por
			// que contar a quem tentar se aquele e-mail tem conta no banco.
			throw ProblemaDeNegocio.naoAutorizado("credenciais-invalidas", "E-mail ou senha incorretos.");
		}
		if (!usuario.podeAutenticar()) {
			throw ProblemaDeNegocio.naoAutorizado("email-nao-verificado",
					"Confirme seu e-mail antes de entrar.");
		}

		this.limitador.liberar(chave);
		return emitirPar(usuario, UUID.randomUUID());
	}

	@Transactional
	public ParDeTokens renovar(String refreshInformado) {
		String hash = TokensOpacos.hashDe(refreshInformado);
		Instant agora = Instant.now();

		RefreshToken token = this.refreshTokens.findByTokenHash(hash)
			.orElseThrow(() -> ProblemaDeNegocio.naoAutorizado("refresh-invalido", "Refresh token invalido."));

		if (token.jaRevogado()) {
			// Um token revogado so reaparece se alguem copiou. Nao da para saber
			// se quem esta chamando e o dono ou o ladrao, entao a familia toda cai.
			this.revogador.revogar(token.getFamilia(), agora);
			throw ProblemaDeNegocio.naoAutorizado("refresh-reutilizado",
					"Sessao encerrada por seguranca. Entre novamente.");
		}
		if (!token.ativoEm(agora)) {
			throw ProblemaDeNegocio.naoAutorizado("refresh-expirado", "Refresh token expirado.");
		}

		token.revogar(agora);
		return emitirPar(token.getUsuario(), token.getFamilia());
	}

	@Transactional
	public void confirmarEmail(String tokenInformado) {
		Instant agora = Instant.now();

		TokenDeVerificacao token = this.verificacoes.findByTokenHash(TokensOpacos.hashDe(tokenInformado))
			.orElseThrow(() -> ProblemaDeNegocio.requisicaoInvalida("verificacao-invalida",
					"Link de verificacao invalido."));

		if (!token.utilizavelEm(agora)) {
			throw ProblemaDeNegocio.requisicaoInvalida("verificacao-expirada",
					"Link de verificacao expirado ou ja utilizado.");
		}

		token.marcarComoUsado(agora);
		token.getUsuario().confirmarEmail();
	}

	private ParDeTokens emitirPar(Usuario usuario, UUID familia) {
		EmissorDeJwt.AccessToken acesso = this.emissor.emitirPara(usuario);

		String refresh = TokensOpacos.gerar();
		Instant expiraEm = Instant.now().plus(this.propriedades.jwt().validadeDoRefresh());
		this.refreshTokens.save(new RefreshToken(usuario, TokensOpacos.hashDe(refresh), familia, expiraEm));

		return new ParDeTokens(acesso.valor(), refresh, acesso.expiraEmSegundos());
	}

	public record ParDeTokens(String accessToken, String refreshToken, long expiraEmSegundos) {
	}

}
